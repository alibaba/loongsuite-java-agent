import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.MultimodalStatus;
import com.aliyun.openservices.log.request.GetLogStoreMultimodalConfigurationRequest;
import com.aliyun.openservices.log.request.PutLogStoreMultimodalConfigurationRequest;
import com.aliyun.openservices.log.response.GetLogStoreMultimodalConfigurationResponse;

/** One-shot helper: enable SLS logstore multimodal configuration. */
public class EnableSlsMultimodal {

  public static void main(String[] args) throws Exception {
    String endpoint = env("ALIBABA_CLOUD_SLS_ENDPOINT", "SLS_ENDPOINT");
    String accessKeyId = env("ALIBABA_CLOUD_ACCESS_KEY_ID", "ALIYUN_ACCESS_KEY_ID", "SLS_ACCESS_KEY_ID");
    String accessKeySecret =
        env("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "ALIYUN_ACCESS_KEY_SECRET", "SLS_ACCESS_KEY_SECRET");
    String project = args.length > 0 ? args[0] : "liuyu-python-test";
    String logstore = args.length > 1 ? args[1] : project;

    if (endpoint == null || accessKeyId == null || accessKeySecret == null) {
      System.err.println("Missing SLS credentials env vars.");
      System.exit(1);
    }

    Client client = new Client(endpoint, accessKeyId, accessKeySecret);
    GetLogStoreMultimodalConfigurationResponse current =
        client.getLogStoreMultimodalConfiguration(
            new GetLogStoreMultimodalConfigurationRequest(project, logstore));
    System.out.println(
        "Current multimodal status: "
            + (current.getStatus() != null ? current.getStatus() : "unknown"));

    client.putLogStoreMultimodalConfiguration(
        new PutLogStoreMultimodalConfigurationRequest(project, logstore, MultimodalStatus.ENABLED));

    GetLogStoreMultimodalConfigurationResponse updated =
        client.getLogStoreMultimodalConfiguration(
            new GetLogStoreMultimodalConfigurationRequest(project, logstore));
    System.out.println(
        "Updated multimodal status: "
            + (updated.getStatus() != null ? updated.getStatus() : "unknown"));
    System.out.println("Logstore " + project + "/" + logstore + " multimodal enabled.");
  }

  private static String env(String... names) {
    for (String name : names) {
      String value = System.getenv(name);
      if (value != null && !value.isEmpty()) {
        return value.trim();
      }
    }
    return null;
  }
}
