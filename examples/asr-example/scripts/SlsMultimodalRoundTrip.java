import com.alibaba.loongsuite.otel.util.genai.SlsObjectData;
import com.alibaba.loongsuite.otel.util.genai.SlsObjectReader;
import com.alibaba.loongsuite.otel.util.genai.SlsUploader;
import com.alibaba.loongsuite.otel.util.genai.MultimodalUploadItem;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** PutObject then GetObject round-trip smoke test. Requires SLS credentials via env vars. */
public class SlsMultimodalRoundTrip {

  public static void main(String[] args) throws Exception {
    String basePath =
        args.length > 0
            ? args[0]
            : env("OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH", "SLS_BASE_PATH");
    if (basePath == null || basePath.isEmpty()) {
      System.err.println(
          "Usage: SlsMultimodalRoundTrip [sls://project/logstore]");
      System.err.println(
          "Or set OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH=sls://my-project/my-logstore");
      System.exit(1);
    }
    String objectName = "test-getobject/" + UUID.randomUUID() + ".txt";
    String uri = basePath + "/" + objectName;
    byte[] payload = ("hello-getobject-" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);

    Map<String, String> meta = new HashMap<String, String>();
    meta.put("trace_id", "roundtrip-test");

    SlsUploader uploader = SlsUploader.tryCreate(basePath);
    if (uploader == null) {
      System.err.println("SlsUploader unavailable");
      System.exit(2);
    }

    MultimodalUploadItem item =
        new MultimodalUploadItem(uri, "text/plain", payload, Collections.unmodifiableMap(meta));
    if (!uploader.upload(item)) {
      System.err.println("Upload rejected (queue full?)");
      System.exit(3);
    }
    uploader.shutdown(10_000);

    Thread.sleep(2000);

    SlsObjectReader reader = SlsObjectReader.tryCreate();
    if (reader == null) {
      System.err.println("SlsObjectReader unavailable");
      System.exit(4);
    }

    SlsObjectData object = reader.getObject(uri);
    String text = new String(object.data(), StandardCharsets.UTF_8);
    System.out.println("URI: " + uri);
    System.out.println("Content-Type: " + object.contentType());
    System.out.println("Payload: " + text);
    System.out.println("Meta trace_id: " + object.meta().get("trace_id"));

    if (!text.equals(new String(payload, StandardCharsets.UTF_8))) {
      System.err.println("Payload mismatch");
      System.exit(5);
    }
    System.out.println("Round-trip OK");
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
