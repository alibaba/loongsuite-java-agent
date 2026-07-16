# Examples Learning Guide

This document teaches AI agents how to learn instrumentation patterns from the
`examples/` directory. **Examples are continuously updated** — always read the live
repository state; this guide describes the discovery process, not a frozen catalog.

## Why Examples Come First

| Source                      | Role                                            | Update frequency                     |
|-----------------------------|-------------------------------------------------|--------------------------------------|
| `examples/` source + README | **Canonical patterns** — real, runnable, tested | High (new scenarios added regularly) |
| `docs/USAGE.md`             | API reference                                   | Medium                               |
| This skill                  | Routing + workflow                              | Medium                               |
| `reference.md`              | Configuration tables                            | Low                                  |

When examples and docs disagree, **trust the examples**.

## Discovery Checklist

Run through this checklist at the start of every instrumentation task:

```
[ ] Read examples/README.md — module table and build commands
[ ] List examples/ subdirectories — find scenario-specific modules
[ ] Read matched module's README.md — trace model diagram
[ ] Read example-common/GenAiConfig.java — Spring/OTel bootstrap
[ ] Read *Service.java files in matched module — span orchestration
[ ] Read application.yml — otel + genai + multimodal config
[ ] Check for new modules not yet documented in this skill
```

### Module name heuristics

| Directory name pattern       | Likely scenario                            |
|------------------------------|--------------------------------------------|
| `basic-example`              | REST API, all GenAI operation types        |
| `asr-example`                | Voice assistant, WebSocket, ASR/TTS        |
| `smart-glasses*`, `glasses*` | Smart glasses / AR wearable (when added)   |
| `ar-*`, `wearable-*`         | AR / wearable device backends (when added) |
| `*-example`                  | General pattern — read README to confirm   |

## Current Modules (as of last skill update)

> **This table may be stale.** Always verify against `examples/README.md`.

### example-common (shared library)

Not a runnable app. Provides reusable bootstrap and helpers.

| File                         | What to learn                                                                                                                                       |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `GenAiConfig.java`           | Spring `@Bean` setup: `OpenTelemetry`, `GenAiTelemetryHandler`, `Tracer`; bridges `otel.*` and `alibaba.cloud.sls.*` from YAML to system properties |
| `GenAiOperations.java`       | Constants for `gen_ai.operation.name` (`chat`, `generate_content`)                                                                                  |
| `VoiceSessionTelemetry.java` | Non-GenAI session span (`websocket.session`) with `gen_ai.conversation.id`                                                                          |
| `CallbackStreamMetrics.java` | Streaming metrics: time-to-first-chunk, per-chunk timing                                                                                            |

**When to reference**: Any Spring Boot app using otel-util-genai. Copy `GenAiConfig` pattern.

### basic-example (REST — all operation types)

**Start here** for standard LLM app instrumentation.

| File                      | Operation         | Key pattern                                                                  |
|---------------------------|-------------------|------------------------------------------------------------------------------|
| `ChatService.java`        | `chat`            | `InferenceInvocation` + `setInputMessages`/`setOutputMessages` + token usage |
| `EmbeddingService.java`   | `embeddings`      | `EmbeddingInvocation` + dimension count                                      |
| `ToolService.java`        | `execute_tool`    | `ToolInvocation` + arguments/result                                          |
| `AgentService.java`       | `invoke_agent`    | `AgentInvocation` local agent                                                |
| `WorkflowService.java`    | `invoke_workflow` | Nested child spans under workflow root                                       |
| `RetrievalService.java`   | `retrieval`       | RAG query + documents                                                        |
| `CreateAgentService.java` | `create_agent`    | Remote agent creation                                                        |
| `ChatController.java`     | —                 | REST endpoint mapping                                                        |
| `BasicOpenAiConfig.java`  | —                 | OpenAI client bean setup                                                     |
| `application.yml`         | —                 | OTLP export + genai capture config                                           |

**Trace model**: flat REST calls, each endpoint creates one GenAI span type.

**Read order**: `GenAiConfig` → `ChatService` → `ToolService` → `AgentService` → `WorkflowService`

### asr-example (voice / smart glasses reference)

**Start here** for voice assistants, smart glasses, wearable audio AI.

| File                           | Role                                                                         |
|--------------------------------|------------------------------------------------------------------------------|
| `VoiceTurnService.java`        | **Primary reference** — full turn orchestration with span hierarchy comments |
| `AsrWebSocketHandler.java`     | WebSocket entry, audio stream handling                                       |
| `AsrTranscriptionService.java` | ASR `generate_content` span, PCM input, streaming                            |
| `LlmService.java`              | Intent `chat` + reply `chat` spans                                           |
| `TtsSynthesisService.java`     | TTS `generate_content` span, WAV output                                      |
| `WeatherToolService.java`      | Conditional `execute_tool` span                                              |
| `application.yml`              | Voice models + optional multimodal SLS upload config                         |
| `scripts/ws_voice_client.py`   | Test client for voice turns                                                  |

**Trace model**:

```
websocket.session
└─ invoke_workflow voice_assistant_turn
   ├─ generate_content {asr-model}
   ├─ chat {llm-model}          (×2: intent + reply)
   ├─ execute_tool {name}       (conditional)
   └─ generate_content {tts-model}
```

**Read order**: `VoiceTurnService.java` (read class-level Javadoc first) → `LlmService` → `AsrTranscriptionService` →
`TtsSynthesisService` → `VoiceSessionTelemetry`

**Smart glasses**: Adapt transport layer (WebSocket → device protocol) but preserve span hierarchy.

## How to Adapt an Example to User Code

### 1. Map business concepts

| Example concept                  | User app concept               |
|----------------------------------|--------------------------------|
| `VoiceTurnService.processTurn()` | One user interaction turn      |
| `ChatService.chat()`             | Single LLM call                |
| `WorkflowService.run()`          | Multi-step pipeline            |
| `websocket.session`              | Device connection / session    |
| `voice_assistant_turn` workflow  | Rename to user's workflow name |

### 2. Copy bootstrap, not business logic

From `GenAiConfig.java`, copy:

- `OpenTelemetry` bean with `AutoConfiguredOpenTelemetrySdk`
- `GenAiTelemetryHandler` bean
- `Tracer` bean
- Property bridging for `otel.*` keys

### 3. Copy span orchestration pattern

From the matched `*Service.java`, copy:

- try-with-resources invocation opening
- `setInputMessages` before LLM call
- `setOutputMessages` + token usage after LLM call
- Nested invocations for workflow children
- `setConversationId` for session tracking (voice scenarios)

### 4. Copy configuration structure

From `application.yml`, copy:

- `otel.semconv.stability.opt.in`
- `otel.instrumentation.genai.*` block
- `otel.resource.attributes` with `service.name` and deployment metadata
- `otel.exporter.otlp.*` endpoints and headers (adapt to user's OTLP backend)
- Optional `multimodal.*` and `alibaba.cloud.sls.*` for audio/image upload

### 5. Explain diffs

When adapting, explicitly tell the user:

- Which example file you based each change on
- What you renamed (workflow name, tool names, models)
- What transport/protocol differs (REST vs WebSocket vs gRPC)

## Multimodal Patterns (from asr-example)

### Inline BlobPart (no external upload)

```java
// ASR input
new InputMessage("user",List.of(new BlobPart("audio/pcm", pcmBytes)));

// TTS output
        new

OutputMessage("assistant",List.of(new BlobPart("audio/wav", wavBytes)),"stop");
```

### External SLS upload (large audio/images)

Enable in `application.yml`:

```yaml
otel.instrumentation.genai:
  capture.message.content: span_and_event
  multimodal.upload.mode: both
  multimodal.storage.base.path: sls://<project>/<logstore>
  multimodal.uploader: sls
```

`GenAiConfig` bridges SLS credentials automatically. Blobs become `UriPart` before span ends.

For smart glasses with camera input, use image MIME types:

```java
new BlobPart("image/jpeg",jpegBytes)
```

## Keeping Up with New Examples

The `examples/` directory is actively maintained. When advising users:

1. **Do not hardcode** the module list from this guide — read `examples/README.md`.
2. **Check git history** or directory listing if the user mentions a scenario not listed here.
3. **Tell the user** when a new official example exists for their use case.
4. If no example exists, combine patterns from the closest modules and say so explicitly.

## Build and Run Commands

```bash
# From repo root
mvn install -DskipTests

# REST example
cd examples/basic-example && mvn spring-boot:run

# Voice example
cd examples/asr-example && mvn spring-boot:run

# Voice test client
python examples/asr-example/scripts/ws_voice_client.py your-16k-mono.wav
```

Suggest the user run the matching example locally to verify trace export before adapting to their app.
