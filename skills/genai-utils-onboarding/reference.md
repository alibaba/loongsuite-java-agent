# LoongSuite Java GenAI Instrumentation — Reference

Detailed reference for the `otel-util-genai` skill.
For instrumentation patterns, prefer `examples/` — see [examples-guide.md](examples-guide.md).

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     User Java Application                           │
├─────────────────────────────────────────────────────────────────────┤
│  Business code — explicit span creation via GenAiTelemetryHandler   │
│       │                                                             │
│       ▼                                                             │
│  otel-util-genai  ── InferenceInvocation, ToolInvocation, etc.     │
│       │                                                             │
│       ▼                                                             │
│  OpenTelemetry SDK + OTLP Exporter                                  │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ OTLP
              Any OTLP-compatible backend (Collector, Jaeger, Grafana, etc.)
```

## Maven Coordinates

| Artifact    | GroupId                  | ArtifactId        |
|-------------|--------------------------|-------------------|
| GenAI utils | `com.alibaba.loongsuite` | `otel-util-genai` |

Maven Central: https://central.sonatype.com/artifact/com.alibaba.loongsuite/otel-util-genai

## Semantic Conventions

| Aspect             | Value                                     |
|--------------------|-------------------------------------------|
| Schema URL         | `https://opentelemetry.io/schemas/1.41.1` |
| Python counterpart | `opentelemetry-util-genai`                |

### LoongSuite Extensions (`OTEL_INSTRUMENTATION_GENAI_EXTENDED_ENABLED`, default `true`)

| Attribute                           | Purpose                                  |
|-------------------------------------|------------------------------------------|
| `gen_ai.span.kind`                  | Logical role: LLM, AGENT, TOOL, WORKFLOW |
| `gen_ai.input.multimodal_metadata`  | Input multimodal metadata                |
| `gen_ai.output.multimodal_metadata` | Output multimodal metadata               |
| `gen_ai.*_ref`                      | References to externally stored content  |

## Environment Variables

### GenAI Library

| Variable                                             | Default  | Description                                      |
|------------------------------------------------------|----------|--------------------------------------------------|
| `OTEL_SEMCONV_STABILITY_OPT_IN`                      | empty    | `gen_ai_latest_experimental`                     |
| `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT` | empty    | `true`/`span_only`/`event_only`/`span_and_event` |
| `OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT`              | inferred | `true`/`false`                                   |
| `OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK`         | empty    | FQCN of CompletionHook                           |
| `OTEL_INSTRUMENTATION_GENAI_EXTENDED_ENABLED`        | `true`   | LoongSuite extended attributes                   |

### Multimodal Upload

| Variable                                                  | Description                    |
|-----------------------------------------------------------|--------------------------------|
| `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOAD_MODE`       | `none`/`input`/`output`/`both` |
| `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH` | e.g. `sls://bucket/path`       |
| `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOADER`          | e.g. `sls`                     |
| `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_AUDIO_CONVERSION`  | PCM→WAV (default `true`)       |

### OTLP Export (Standard OpenTelemetry SDK)

Works with any OTLP-compatible observability backend.

| Variable                              | Purpose                                                            |
|---------------------------------------|--------------------------------------------------------------------|
| `OTEL_SERVICE_NAME`                   | Service identity                                                   |
| `OTEL_TRACES_EXPORTER`                | e.g. `otlp`                                                        |
| `OTEL_METRICS_EXPORTER`               | e.g. `otlp`                                                        |
| `OTEL_EXPORTER_OTLP_ENDPOINT`         | Base OTLP endpoint (default `http://localhost:4318`)               |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`  | Trace-specific endpoint (optional override)                        |
| `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | Metrics-specific endpoint (optional override)                      |
| `OTEL_EXPORTER_OTLP_HEADERS`          | Backend-specific auth headers                                      |
| `OTEL_EXPORTER_OTLP_PROTOCOL`         | `http/protobuf` or `grpc`                                          |
| `OTEL_RESOURCE_ATTRIBUTES`            | Resource attributes, e.g. `service.name`, `deployment.environment` |

Example:

```bash
export OTEL_SERVICE_NAME=my-genai-app
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
```

## API Quick Reference

### Handler

```java
GenAiTelemetryHandler handler = GenAiTelemetryHandler.create(openTelemetry);
```

### Operations

| Operation          | Factory                                                    | SpanKind        |
|--------------------|------------------------------------------------------------|-----------------|
| `chat`             | `handler.inference(provider, model)`                       | CLIENT          |
| `generate_content` | `handler.inference(..., "generate_content")`               | CLIENT          |
| `embeddings`       | `handler.embedding(provider, model)`                       | CLIENT          |
| `execute_tool`     | `handler.tool(name)`                                       | INTERNAL        |
| `invoke_agent`     | `handler.invokeLocalAgent(...)` / `invokeRemoteAgent(...)` | INTERNAL/CLIENT |
| `invoke_workflow`  | `handler.workflow(name)`                                   | INTERNAL        |
| `retrieval`        | `handler.retrieval(provider, dataSourceId, ...)`           | CLIENT          |
| `create_agent`     | `handler.createAgent(...)`                                 | CLIENT          |

Working implementations: see `examples/basic-example/` and `examples/asr-example/`.

### Metrics

- `gen_ai.client.operation.duration`
- `gen_ai.client.token.usage`
- `gen_ai.client.operation.time_to_first_chunk`
- `gen_ai.client.operation.time_per_output_chunk`

## Troubleshooting

| Symptom                              | Fix                                                                           |
|--------------------------------------|-------------------------------------------------------------------------------|
| No traces at backend                 | Verify OTel SDK + OTLP exporter configured; check endpoint and headers        |
| No message content                   | Set capture mode; call `setInputMessages`/`setOutputMessages` before span end |
| Error spans show OK                  | Use `inv.fail(e)` or `*Run()` — try-with-resources does not auto-fail         |
| Large audio/image missing from spans | Enable multimodal upload; see `asr-example/README.md`                         |

## Repository File Index

| File                                             | Content                                |
|--------------------------------------------------|----------------------------------------|
| `examples/README.md`                             | **Living** module index — read first   |
| `examples/example-common/.../GenAiConfig.java`   | Bootstrap template                     |
| `examples/asr-example/.../VoiceTurnService.java` | Voice/smart-glasses span orchestration |
| `examples/basic-example/.../ChatService.java`    | Chat span template                     |
| `docs/USAGE.md`                                  | Full API guide                         |
| `skills/otel-util-genai/SKILL.md`                | This skill                             |
