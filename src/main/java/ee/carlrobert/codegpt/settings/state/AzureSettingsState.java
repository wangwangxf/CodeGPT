package ee.carlrobert.codegpt.settings.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import ee.carlrobert.codegpt.credentials.AzureCredentialsManager;
import ee.carlrobert.codegpt.settings.ServiceSelectionForm;
import ee.carlrobert.llm.client.openai.completion.chat.OpenAIChatCompletionModel;
import org.jetbrains.annotations.NotNull;

@State(name = "CodeGPT_AzureSettings_210", storages = @Storage("CodeGPT_AzureSettings_210.xml"))
public class AzureSettingsState implements PersistentStateComponent<AzureSettingsState> {

  private final String BASE_PATH = "/openai/deployments/%s/chat/completions?api-version=%s";

  private String resourceName = "";
  private String deploymentId = "";
  private String apiVersion = "";
  private String baseHost = "https://%s.openai.azure.com";
  private String path = BASE_PATH;
  private String model = OpenAIChatCompletionModel.GPT_3_5.getCode();
  private boolean useAzureApiKeyAuthentication = true;
  private boolean useAzureActiveDirectoryAuthentication;

  public static AzureSettingsState getInstance() {
    return ApplicationManager.getApplication().getService(AzureSettingsState.class);
  }

  @Override
  public AzureSettingsState getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AzureSettingsState state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean isModified(ServiceSelectionForm serviceSelectionForm) {
    return serviceSelectionForm.isAzureActiveDirectoryAuthenticationSelected() != isUseAzureActiveDirectoryAuthentication() ||
        serviceSelectionForm.isAzureApiKeyAuthenticationSelected() != isUseAzureApiKeyAuthentication() ||
        !serviceSelectionForm.getAzureActiveDirectoryToken().equals(AzureCredentialsManager.getInstance().getAzureActiveDirectoryToken()) ||
        !serviceSelectionForm.getAzureOpenAIApiKey().equals(AzureCredentialsManager.getInstance().getAzureOpenAIApiKey()) ||
        !serviceSelectionForm.getAzureResourceName().equals(resourceName) ||
        !serviceSelectionForm.getAzureDeploymentId().equals(deploymentId) ||
        !serviceSelectionForm.getAzureApiVersion().equals(apiVersion) ||
        !serviceSelectionForm.getAzureBaseHost().equals(baseHost) ||
        !serviceSelectionForm.getAzurePath().equals(path) ||
        !serviceSelectionForm.getAzureModel().equals(model);
  }

  public void apply(ServiceSelectionForm serviceSelectionForm) {
    useAzureActiveDirectoryAuthentication = serviceSelectionForm.isAzureActiveDirectoryAuthenticationSelected();
    useAzureApiKeyAuthentication = serviceSelectionForm.isAzureApiKeyAuthenticationSelected();

    resourceName = serviceSelectionForm.getAzureResourceName();
    deploymentId = serviceSelectionForm.getAzureDeploymentId();
    apiVersion = serviceSelectionForm.getAzureApiVersion();
    baseHost = serviceSelectionForm.getAzureBaseHost();
    path = serviceSelectionForm.getAzurePath();
    model = serviceSelectionForm.getAzureModel();
  }

  public void reset(ServiceSelectionForm serviceSelectionForm) {
    serviceSelectionForm.setAzureApiKey(AzureCredentialsManager.getInstance().getAzureOpenAIApiKey());
    serviceSelectionForm.setAzureActiveDirectoryToken(AzureCredentialsManager.getInstance().getAzureActiveDirectoryToken());
    serviceSelectionForm.setAzureApiKeyAuthenticationSelected(useAzureApiKeyAuthentication);
    serviceSelectionForm.setAzureActiveDirectoryAuthenticationSelected(useAzureActiveDirectoryAuthentication);
    serviceSelectionForm.setAzureResourceName(resourceName);
    serviceSelectionForm.setAzureDeploymentId(deploymentId);
    serviceSelectionForm.setAzureApiVersion(apiVersion);
    serviceSelectionForm.setAzureBaseHost(baseHost);
    serviceSelectionForm.setAzurePath(path);
    serviceSelectionForm.setAzureModel(serviceSelectionForm.getAzureModel());
  }

  public boolean isUsingCustomPath() {
    return !BASE_PATH.equals(path);
  }

  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public void setDeploymentId(String deploymentId) {
    this.deploymentId = deploymentId;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public String getBaseHost() {
    return baseHost;
  }

  public void setBaseHost(String baseHost) {
    this.baseHost = baseHost;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public boolean isUseAzureApiKeyAuthentication() {
    return useAzureApiKeyAuthentication;
  }

  public void setUseAzureApiKeyAuthentication(boolean useAzureApiKeyAuthentication) {
    this.useAzureApiKeyAuthentication = useAzureApiKeyAuthentication;
  }

  public boolean isUseAzureActiveDirectoryAuthentication() {
    return useAzureActiveDirectoryAuthentication;
  }

  public void setUseAzureActiveDirectoryAuthentication(boolean useAzureActiveDirectoryAuthentication) {
    this.useAzureActiveDirectoryAuthentication = useAzureActiveDirectoryAuthentication;
  }
}
