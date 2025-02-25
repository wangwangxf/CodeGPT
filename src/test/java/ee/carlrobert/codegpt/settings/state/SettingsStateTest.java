package ee.carlrobert.codegpt.settings.state;


import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import ee.carlrobert.codegpt.conversations.Conversation;

public class SettingsStateTest extends BasePlatformTestCase {

  public void testOpenAISettingsSync() {
    var settings = SettingsState.getInstance();
    var openAISettings = OpenAISettingsState.getInstance();
    openAISettings.setModel("gpt-3.5-turbo");
    var conversation = new Conversation();
    conversation.setModel("gpt-4");
    conversation.setClientCode("chat.completion");

    settings.sync(conversation);

    assertThat(settings)
        .extracting("useOpenAIService", "useAzureService", "useYouService")
        .containsExactly(true, false, false);
    assertThat(openAISettings.getModel()).isEqualTo("gpt-4");
  }

  public void testAzureSettingsSync() {
    var settings = SettingsState.getInstance();
    var azureSettings = AzureSettingsState.getInstance();
    azureSettings.setModel("gpt-3.5-turbo");
    var conversation = new Conversation();
    conversation.setModel("gpt-4");
    conversation.setClientCode("azure.chat.completion");

    settings.sync(conversation);

    assertThat(settings)
        .extracting("useOpenAIService", "useAzureService", "useYouService")
        .containsExactly(false, true, false);
    assertThat(azureSettings.getModel()).isEqualTo("gpt-4");
  }

  public void testYouSettingsSync() {
    var settings = SettingsState.getInstance();
    var conversation = new Conversation();
    conversation.setModel("YouCode");
    conversation.setClientCode("you.chat.completion");

    settings.sync(conversation);

    assertThat(settings)
        .extracting("useOpenAIService", "useAzureService", "useYouService")
        .containsExactly(false, false, true);
  }
}
