package ee.carlrobert.codegpt.conversations;

import static java.util.stream.Collectors.toList;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import ee.carlrobert.codegpt.conversations.message.Message;
import ee.carlrobert.codegpt.settings.state.AzureSettingsState;
import ee.carlrobert.codegpt.settings.state.OpenAISettingsState;
import ee.carlrobert.codegpt.settings.state.SettingsState;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

@Service
public final class ConversationService {

  private final ConversationsState conversationState = ConversationsState.getInstance();

  private ConversationService() {
  }

  public static ConversationService getInstance() {
    return ApplicationManager.getApplication().getService(ConversationService.class);
  }

  public List<Conversation> getSortedConversations() {
    return conversationState.getConversationsMapping()
        .values()
        .stream()
        .flatMap(List::stream)
        .sorted(Comparator.comparing(Conversation::getUpdatedOn).reversed())
        .collect(toList());
  }

  public Conversation createConversation(String clientCode) {
    var settings = SettingsState.getInstance();
    var conversation = new Conversation();
    conversation.setId(UUID.randomUUID());
    conversation.setClientCode(clientCode);
    if (settings.isUseYouService()) {
      conversation.setModel("YouCode");
    } else if (settings.isUseAzureService()) {
      conversation.setModel(AzureSettingsState.getInstance().getModel());
    } else {
      conversation.setModel(OpenAISettingsState.getInstance().getModel());
    }
    conversation.setCreatedOn(LocalDateTime.now());
    conversation.setUpdatedOn(LocalDateTime.now());
    return conversation;
  }

  public void addConversation(Conversation conversation) {
    var conversationsMapping = conversationState.getConversationsMapping();
    var conversations = conversationsMapping.get(conversation.getClientCode());
    if (conversations == null) {
      conversations = new ArrayList<>();
    }
    conversations.add(conversation);
    conversationsMapping.put(conversation.getClientCode(), conversations);
  }

  public void saveMessage(String response, Message message, Conversation conversation, boolean isRetry) {
    var conversationMessages = conversation.getMessages();
    if (isRetry && !conversationMessages.isEmpty()) {
      var messageToBeSaved = conversationMessages.stream()
          .filter(item -> item.getId().equals(message.getId()))
          .findFirst().orElseThrow();
      messageToBeSaved.setResponse(response);
      saveConversation(conversation);
      return;
    }

    message.setResponse(response);
    conversation.addMessage(message);
    saveConversation(conversation);
  }

  public void saveMessage(@NotNull Conversation conversation, @NotNull Message message) {
    conversation.setUpdatedOn(LocalDateTime.now());
    var iterator = conversationState.getConversationsMapping()
        .get(conversation.getClientCode())
        .listIterator();
    while (iterator.hasNext()) {
      var next = iterator.next();
      next.setMessages(
          next.getMessages().stream().map(item -> {
            if (item.getId() == message.getId()) {
              return message;
            }
            return item;
          }).collect(toList()));
      if (next.getId().equals(conversation.getId())) {
        iterator.set(conversation);
      }
    }
  }

  public void saveConversation(Conversation conversation) {
    conversation.setUpdatedOn(LocalDateTime.now());
    var iterator = conversationState.getConversationsMapping()
        .get(conversation.getClientCode())
        .listIterator();
    while (iterator.hasNext()) {
      var next = iterator.next();
      if (next.getId().equals(conversation.getId())) {
        iterator.set(conversation);
      }
    }
    conversationState.setCurrentConversation(conversation);
  }

  private String getClientCode() {
    var settings = SettingsState.getInstance();
    if (settings.isUseOpenAIService()) {
      return "chat.completion";
    }
    if (settings.isUseAzureService()) {
      return "azure.chat.completion";
    }
    return "you.chat.completion";
  }

  public Conversation startConversation() {
    var conversation = createConversation(getClientCode());
    conversationState.setCurrentConversation(conversation);
    addConversation(conversation);
    return conversation;
  }

  public void clearAll() {
    conversationState.getConversationsMapping().clear();
    conversationState.setCurrentConversation(null);
  }

  public Optional<Conversation> getPreviousConversation() {
    return tryGetNextOrPreviousConversation(true);
  }

  public Optional<Conversation> getNextConversation() {
    return tryGetNextOrPreviousConversation(false);
  }

  private Optional<Conversation> tryGetNextOrPreviousConversation(boolean isPrevious) {
    var currentConversation = ConversationsState.getCurrentConversation();
    if (currentConversation != null) {
      var sortedConversations = getSortedConversations();
      for (int i = 0; i < sortedConversations.size(); i++) {
        var conversation = sortedConversations.get(i);
        if (conversation != null && conversation.getId().equals(currentConversation.getId())) {
          // higher index indicates older conversation
          var previousIndex = isPrevious ? i + 1 : i - 1;
          if (isPrevious ? previousIndex < sortedConversations.size() : previousIndex != -1) {
            return Optional.of(sortedConversations.get(previousIndex));
          }
        }
      }
    }
    return Optional.empty();
  }

  public void deleteConversation(Conversation conversation) {
    var iterator = conversationState.getConversationsMapping()
        .get(conversation.getClientCode())
        .listIterator();
    while (iterator.hasNext()) {
      var next = iterator.next();
      if (next.getId().equals(conversation.getId())) {
        iterator.remove();
        break;
      }
    }
  }

  public void deleteSelectedConversation() {
    var nextConversation = getPreviousConversation();
    if (nextConversation.isEmpty()) {
      nextConversation = getNextConversation();
    }

    var currentConversation = ConversationsState.getCurrentConversation();
    if (currentConversation != null) {
      deleteConversation(currentConversation);
      nextConversation.ifPresent(conversationState::setCurrentConversation);
    } else {
      throw new RuntimeException("Tried to delete a conversation that hasn't been set");
    }
  }

  public void discardTokenLimits(Conversation conversation) {
    conversation.discardTokenLimits();
    saveConversation(conversation);
  }
}