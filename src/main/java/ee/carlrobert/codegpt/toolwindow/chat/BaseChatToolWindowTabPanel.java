package ee.carlrobert.codegpt.toolwindow.chat;

import static com.intellij.openapi.ui.Messages.OK;
import static ee.carlrobert.codegpt.util.ThemeUtils.getPanelBackgroundColor;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.OnOffButton;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.Borders;
import ee.carlrobert.codegpt.actions.ActionType;
import ee.carlrobert.codegpt.completions.CompletionRequestHandler;
import ee.carlrobert.codegpt.completions.you.YouSerpResult;
import ee.carlrobert.codegpt.conversations.Conversation;
import ee.carlrobert.codegpt.conversations.ConversationService;
import ee.carlrobert.codegpt.conversations.message.Message;
import ee.carlrobert.codegpt.credentials.AzureCredentialsManager;
import ee.carlrobert.codegpt.credentials.OpenAICredentialsManager;
import ee.carlrobert.codegpt.settings.state.AzureSettingsState;
import ee.carlrobert.codegpt.settings.state.OpenAISettingsState;
import ee.carlrobert.codegpt.settings.state.SettingsState;
import ee.carlrobert.codegpt.settings.state.YouSettingsState;
import ee.carlrobert.codegpt.telemetry.TelemetryAction;
import ee.carlrobert.codegpt.toolwindow.ModelIconLabel;
import ee.carlrobert.codegpt.toolwindow.chat.components.ChatMessageResponseBody;
import ee.carlrobert.codegpt.toolwindow.chat.components.ResponsePanel;
import ee.carlrobert.codegpt.toolwindow.chat.components.SmartScroller;
import ee.carlrobert.codegpt.toolwindow.chat.components.UserMessagePanel;
import ee.carlrobert.codegpt.toolwindow.chat.components.UserPromptTextArea;
import ee.carlrobert.codegpt.util.EditorUtils;
import ee.carlrobert.codegpt.util.OverlayUtils;
import ee.carlrobert.codegpt.util.file.FileUtils;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseChatToolWindowTabPanel implements ChatToolWindowTabPanel {

  private final boolean useContextualSearch;
  private final JPanel rootPanel;
  private final ScrollablePanel scrollablePanel;
  private final Map<UUID, JPanel> visibleMessagePanels = new HashMap<>();
  private final Map<UUID, List<YouSerpResult>> serpResultsMapping = new HashMap<>();

  protected final Project project;
  protected final UserPromptTextArea userPromptTextArea;
  protected final ConversationService conversationService;
  protected @Nullable Conversation conversation;

  protected abstract JComponent getLandingView();

  public BaseChatToolWindowTabPanel(@NotNull Project project, boolean useContextualSearch) {
    this.project = project;
    this.useContextualSearch = useContextualSearch;
    this.conversationService = ConversationService.getInstance();
    this.rootPanel = new JPanel(new GridBagLayout());
    this.scrollablePanel = new ScrollablePanel();
    this.userPromptTextArea = new UserPromptTextArea(this::handleSubmit);
    init();
  }

  public void requestFocusForTextArea() {
    userPromptTextArea.focus();
  }

  @Override
  public JPanel getContent() {
    return rootPanel;
  }

  @Override
  public @Nullable Conversation getConversation() {
    return conversation;
  }

  @Override
  public void setConversation(@Nullable Conversation conversation) {
    this.conversation = conversation;
  }

  @Override
  public void displayLandingView() {
    scrollablePanel.removeAll();
    scrollablePanel.add(getLandingView());
    scrollablePanel.repaint();
    scrollablePanel.revalidate();
  }

  @Override
  public void startNewConversation(Message message) {
    conversation = conversationService.startConversation();
    sendMessage(message);
  }

  @Override
  public void sendMessage(Message message) {
    if (conversation == null) {
      conversation = conversationService.startConversation();
    }

    var messageWrapper = createNewMessageWrapper(message.getId());
    messageWrapper.add(new UserMessagePanel(
        project,
        message,
        message.getUserMessage() != null,
        this));
    var responsePanel = new ResponsePanel()
        .withReloadAction(() -> reloadMessage(message, conversation))
        .withDeleteAction(() -> deleteMessage(message.getId(), messageWrapper, conversation))
        .addContent(new ChatMessageResponseBody(project, true, this));
    messageWrapper.add(responsePanel);
    call(conversation, message, responsePanel, false);
  }

  @Override
  public void dispose() {
  }

  private boolean isRequestAllowed() {
    if (SettingsState.getInstance().isUseAzureService()) {
      return AzureCredentialsManager.getInstance().isCredentialSet();
    }
    return OpenAICredentialsManager.getInstance().isApiKeySet();
  }

  private void call(
      Conversation conversation,
      Message message,
      ResponsePanel responsePanel,
      boolean isRetry) {
    ChatMessageResponseBody responseContainer = (ChatMessageResponseBody) responsePanel.getContent();

    if (!isRequestAllowed()) {
      responseContainer.displayMissingCredential();
      return;
    }

    var requestHandler = new CompletionRequestHandler();
    requestHandler.withContextualSearch(useContextualSearch);
    requestHandler.addMessageListener(partialMessage -> {
      try {
        responseContainer.update(partialMessage);
      } catch (Exception e) {
        responseContainer.displayDefaultError();
        throw new RuntimeException("Error while updating the content", e);
      }
    });
    requestHandler.addRequestCompletedListener(completeMessage -> {
      responsePanel.enableActions();
      conversationService.saveMessage(completeMessage, message, conversation, isRetry);
      stopStreaming(responseContainer);

      var serpResults = serpResultsMapping.get(message.getId());
      var containsResults = serpResults != null && !serpResults.isEmpty();
      if (YouSettingsState.getInstance().isDisplayWebSearchResults()) {
        if (containsResults) {
          responseContainer.displaySerpResults(serpResults);
        }
      }

      if (containsResults) {
        message.setSerpResults(serpResults.stream()
            .map(result -> new YouSerpResult(
                result.getUrl(),
                result.getName(),
                result.getSnippet(),
                result.getSnippetSource()))
            .collect(toList()));
      }
    });
    requestHandler.addTokensExceededListener(() -> SwingUtilities.invokeLater(() -> {
      var answer = OverlayUtils.showTokenLimitExceededDialog();
      if (answer == OK) {
        TelemetryAction.IDE_ACTION.createActionMessage()
            .property("action", "DISCARD_TOKEN_LIMIT")
            .property("model", conversation.getModel())
            .send();

        conversationService.discardTokenLimits(conversation);
        requestHandler.call(conversation, message, true);
      } else {
        stopStreaming(responseContainer);
      }
    }));
    requestHandler.addErrorListener((error, ex) -> {
      try {
        if ("insufficient_quota".equals(error.getCode())) {
          responseContainer.displayQuotaExceeded();
        } else {
          responseContainer.displayError(error.getMessage());
        }
      } finally {
        responsePanel.enableActions();
        stopStreaming(responseContainer);
      }
    });
    requestHandler.addSerpResultsListener(
        serpResults -> serpResultsMapping.put(message.getId(), serpResults.stream()
            .map(result -> new YouSerpResult(
                result.getUrl(),
                result.getName(),
                result.getSnippet(),
                result.getSnippetSource()))
            .collect(toList())));
    userPromptTextArea.setRequestHandler(requestHandler);
    userPromptTextArea.setSubmitEnabled(false);
    requestHandler.call(conversation, message, isRetry);
  }

  protected void reloadMessage(Message message, Conversation conversation) {
    ResponsePanel responsePanel = null;
    try {
      responsePanel = (ResponsePanel) Arrays.stream(
              visibleMessagePanels.get(message.getId()).getComponents())
          .filter(component -> component instanceof ResponsePanel)
          .findFirst().orElseThrow();
      ((ChatMessageResponseBody) responsePanel.getContent()).clear();
      scrollablePanel.revalidate();
      scrollablePanel.repaint();
    } catch (Exception e) {
      throw new RuntimeException("Couldn't reload message", e);
    } finally {
      if (responsePanel != null) {
        message.setResponse("");
        conversationService.saveMessage(conversation, message);
        call(conversation, message, responsePanel, true);
      }

      TelemetryAction.IDE_ACTION.createActionMessage()
          .property("action", ActionType.RELOAD_MESSAGE.name())
          .send();
    }
  }

  protected void deleteMessage(UUID messageId, JPanel messageWrapper, Conversation conversation) {
    scrollablePanel.remove(messageWrapper);
    scrollablePanel.repaint();
    scrollablePanel.revalidate();

    visibleMessagePanels.remove(messageId);
    conversation.removeMessage(messageId);
    conversationService.saveConversation(conversation);

    if (conversation.getMessages().isEmpty()) {
      conversationService.deleteConversation(conversation);
      setConversation(null);
      displayLandingView();
    }
  }

  protected JPanel createNewMessageWrapper(UUID messageId) {
    var messageWrapper = new JPanel();
    messageWrapper.setLayout(new BoxLayout(messageWrapper, BoxLayout.PAGE_AXIS));
    scrollablePanel.add(messageWrapper);
    scrollablePanel.repaint();
    scrollablePanel.revalidate();
    visibleMessagePanels.put(messageId, messageWrapper);
    return messageWrapper;
  }

  protected void clearWindow() {
    scrollablePanel.removeAll();
    scrollablePanel.revalidate();
    scrollablePanel.repaint();
  }

  private void stopStreaming(ChatMessageResponseBody responseContainer) {
    SwingUtilities.invokeLater(() -> {
      userPromptTextArea.setSubmitEnabled(true);
      responseContainer.hideCarets();
    });
  }

  private void handleSubmit(String text) {
    var message = new Message(text);
    var editor = EditorUtils.getSelectedEditor(project);
    if (editor != null) {
      var selectionModel = editor.getSelectionModel();
      var selectedText = selectionModel.getSelectedText();
      if (selectedText != null && !selectedText.isEmpty()) {
        var fileExtension = FileUtils.getFileExtension(
            ((EditorImpl) editor).getVirtualFile().getName());
        message = new Message(text + format("\n```%s\n%s\n```", fileExtension, selectedText));
        message.setUserMessage(text);
        selectionModel.removeSelection();
      }
    }

    if (conversation == null) {
      startNewConversation(message);
    } else {
      sendMessage(message);
    }
  }

  private void init() {
    var gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1;
    gbc.weightx = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;

    scrollablePanel.setLayout(new BoxLayout(scrollablePanel, BoxLayout.Y_AXIS));
    JBScrollPane scrollPane = new JBScrollPane(scrollablePanel);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    scrollPane.setViewportBorder(null);
    rootPanel.add(scrollPane, gbc);
    new SmartScroller(scrollPane);

    gbc.weighty = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridy = 1;

    var model = getModel();
    var modelIconWrapper = JBUI.Panels
        .simplePanel(new ModelIconLabel(getClientCode(), model))
        .withBorder(Borders.emptyRight(4))
        .withBackground(getPanelBackgroundColor());

    var wrapper = new JPanel(new BorderLayout());
    wrapper.setBorder(JBUI.Borders.compound(
        JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
        JBUI.Borders.empty(8)));
    wrapper.setBackground(getPanelBackgroundColor());
    wrapper.add(userPromptTextArea, BorderLayout.SOUTH);
    if (model != null) {
      var header = new JPanel(new BorderLayout());
      header.setBackground(getPanelBackgroundColor());
      header.setBorder(JBUI.Borders.emptyBottom(8));
      if ("YouCode".equals(model)) {
        var gpt4ToggleButton = new OnOffButton();
        var useGPT4Model = YouSettingsState.getInstance().isUseGPT4Model();
        gpt4ToggleButton.setSelected(useGPT4Model);
        gpt4ToggleButton.setOnText("GPT-4");
        gpt4ToggleButton.setOffText("GPT-4");
        gpt4ToggleButton.addActionListener(
            actionEvent -> {
              project.getMessageBus()
                  .syncPublisher(YouModelChangeNotifier.YOU_MODEL_CHANGE_NOTIFIER_TOPIC)
                  .modelChanged(gpt4ToggleButton.isSelected());
              YouSettingsState.getInstance().setUseGPT4Model(gpt4ToggleButton.isSelected());
              installHelpTooltip(gpt4ToggleButton.isSelected(), gpt4ToggleButton);
            });
        project.getMessageBus()
            .connect()
            .subscribe(
                YouModelChangeNotifier.YOU_MODEL_CHANGE_NOTIFIER_TOPIC,
                (YouModelChangeNotifier) gpt4ToggleButton::setSelected);

        installHelpTooltip(useGPT4Model, gpt4ToggleButton);
        header.add(gpt4ToggleButton, BorderLayout.LINE_START);
      }
      header.add(modelIconWrapper, BorderLayout.LINE_END);
      wrapper.add(header);
    }
    rootPanel.add(wrapper, gbc);
    userPromptTextArea.requestFocusInWindow();
    userPromptTextArea.requestFocus();
  }

  private void installHelpTooltip(boolean selected, JComponent component) {
    new HelpTooltip()
        .setDescription(selected ? "Turn off for faster responses" : "Turn on for complex queries")
        .installOn(component);
  }

  private String getClientCode() {
    var settings = SettingsState.getInstance();
    if (settings.isUseOpenAIService()) {
      return "chat.completion";
    }
    if (settings.isUseAzureService()) {
      return "azure.chat.completion";
    }
    if (settings.isUseYouService()) {
      return "you.chat.completion";
    }
    return null;
  }

  private @Nullable String getModel() {
    var settings = SettingsState.getInstance();
    if (settings.isUseOpenAIService()) {
      return OpenAISettingsState.getInstance().getModel();
    }
    if (settings.isUseAzureService()) {
      return AzureSettingsState.getInstance().getModel();
    }
    if (settings.isUseYouService()) {
      return "YouCode";
    }

    return null;
  }
}
