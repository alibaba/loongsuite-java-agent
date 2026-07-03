# basic-example

REST API demonstrating all well-known `gen_ai.operation.name` values from OTel GenAI Semantic Conventions v1.41.1.

## Run

```bash
# Edit src/main/resources/application.yml — replace <your-dashscope-api-key> and CMS placeholders

cd examples/basic-example
mvn spring-boot:run
```

## Endpoints

| Endpoint | operation.name |
|----------|----------------|
| `POST /api/chat` | `chat` |
| `POST /api/embedding` | `embeddings` |
| `POST /api/tool` | `execute_tool` |
| `POST /api/agent` | `invoke_agent` |
| `POST /api/workflow` | `invoke_workflow` |
| `POST /api/retrieval` | `retrieval` |
| `POST /api/create-agent` | `create_agent` |

### Example

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello"}'
```

Configuration: `src/main/resources/application.yml`

## otel-util-genai required settings

| Property | Purpose | Env var |
|----------|---------|---------|
| `otel.semconv.stability.opt.in` | Enable GenAI experimental semconv | `OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental` |
| `otel.instrumentation.genai.capture.message.content` | Message content capture mode | `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT=span_and_event` |
| `otel.instrumentation.genai.emit.event` | Emit OTel Log Events (recommended `true`) | `OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT=true` |

### Alibaba Cloud CMS/ARMS GenAI view

When exporting OTLP to CMS, add to `OTEL_RESOURCE_ATTRIBUTES`:

```
gen_ai.instrumentation.sdk.name=loongsuite-genai-utils
acs.arms.service.feature=genai_app
acs.cms.workspace=<your-workspace-id>
```

See comments in `application.yml`.
