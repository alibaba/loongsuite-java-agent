import com.alibaba.loongsuite.otel.util.genai.SlsObjectData;
import com.alibaba.loongsuite.otel.util.genai.SlsObjectReader;
import java.io.FileOutputStream;
import java.util.Map;

/** CLI: download a multimodal object from SLS via GetObject. */
public class GetSlsMultimodalObject {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: GetSlsMultimodalObject <sls://project/logstore/object/path> [output-file]");
      System.exit(1);
    }
    String uri = args[0];
    String output = args.length > 1 ? args[1] : "/tmp/sls-object.bin";

    SlsObjectReader reader = SlsObjectReader.tryCreate();
    if (reader == null) {
      System.err.println("SlsObjectReader unavailable. Check aliyun-log >= 0.6.155 and SLS env vars.");
      System.exit(2);
    }

    SlsObjectData object = reader.getObject(uri);
    FileOutputStream out = new FileOutputStream(output);
    try {
      out.write(object.data());
    } finally {
      out.close();
    }

    System.out.println("URI: " + object.uri());
    System.out.println("Content-Type: " + object.contentType());
    System.out.println("Size: " + object.size() + " bytes");
    System.out.println("Saved to: " + output);
    if (!object.meta().isEmpty()) {
      System.out.println("Metadata:");
      for (Map.Entry<String, String> entry : object.meta().entrySet()) {
        System.out.println("  " + entry.getKey() + "=" + entry.getValue());
      }
    }
  }
}
