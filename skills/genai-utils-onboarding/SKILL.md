---
name: otel-util-genai
description: >-
  Guide users to manually instrument Java GenAI applications with otel-util-genai.
  Use when the user needs OTel GenAI manual instrumentation: chat, Agent/Tool/Workflow/
  Retrieval spans, voice/multimodal/smart-glasses telemetry. Works with any OTLP backend.
  Load from GitHub: alibaba/loongsuite-java/skills/otel-util-genai/
license: Apache-2.0
metadata:
  domain: observability
  owner: loongsuite
  repository: https://github.com/alibaba/loongsuite-java
  skill_path: skills/otel-util-genai/
---

# LoongSuite Java GenAI Instrumentation (otel-util-genai)

Help users add **manual GenAI telemetry** to Java applications using `otel-util-genai`
from the [loongsuite-java](https://github.com/alibaba/loongsuite-java) repository.

> **Scope**: OTel GenAI **manual instrumentation** only — `otel-util-genai` library +
> OpenTelemetry SDK. All spans (chat, tool, agent, workflow, retrieval, multimodal) are
> created explicitly in application code.

## Integration with AgentLoop

When **alibabacloud-agentloop-management** determines the user needs Java GenAI Utils
instrumentation (custom AI app, not a pre-built framework addon), load this skill from GitHub:

```
https://github.com/alibaba/loongsuite-java/tree/main/skills/otel-util-genai
```

Typical AgentLoop routing:

| User scenario                                           | Use this skill               |
|---------------------------------------------------------|------------------------------|
| Custom Java LLM app needing manual GenAI spans          | Yes                          |
| Agent/Tool/Workflow/Retrieval instrumentation           | Yes                          |
| Voice assistant / smart glasses Java backend            | Yes — learn from `examples/` |
| Multimodal (ASR/TTS/audio/image) manual instrumentation | Yes                          |
| OTLP export to any observability backend                | Yes                          |

## Mandatory: Examples-First Workflow

**The `examples/` directory is the living source of truth.** It is continuously updated
with new scenarios. Do NOT rely only on static text in this skill — always discover
current examples before advising users.

### Step 0 — Discover Current Examples (ALWAYS DO THIS FIRST)

Before writing instrumentation guidance, read these files from the repository:

1. `examples/README.md` — module index and quick-start commands
2. `examples/pom.xml` — list of active example modules
3. Each example's `README.md` — scenario-specific trace model and key classes

Scan for scenario-specific modules by name:

```bash
ls examples/
# Look for: basic-example, asr-example, smart-glasses*, glasses*, ar-*, etc.
```

**Rule**: If a dedicated example exists for the user's scenario (e.g. smart glasses),
use that example as the **primary reference**. Adapt its patterns to the user's codebase.
If no dedicated example exists yet, fall back to the closest match (see Scenario Routing below).

### Step 1 — Read Relevant Example Source Code

For the matched example, read these files in order:

| Layer                    | Files to read                                                                                                                |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Bootstrap                | `examples/example-common/src/main/java/.../GenAiConfig.java`                                                                 |
| Shared helpers           | `examples/example-common/src/main/java/.../GenAiOperations.java`, `VoiceSessionTelemetry.java`, `CallbackStreamMetrics.java` |
| Business instrumentation | `examples/<module>/src/main/java/**/*Service.java`                                                                           |
| Configuration            | `examples/<module>/src/main/resources/application.yml`                                                                       |
| API surface              | `examples/<module>/src/main/java/**/*Controller.java` or `**/ws/*`                                                           |

Mirror the example's patterns (span hierarchy, try-with-resources, message types) in the
user's application. Cite specific example file paths when explaining changes.

### Step 2 — Cross-Check API Docs

After reading examples, consult static docs only for gaps:

- `docs/USAGE.md` — full API reference
- [reference.md](reference.md) in this skill — configuration tables and OTLP export settings

**Priority order**: `examples/` source code > example README > `docs/USAGE.md` > this skill.

## Scenario Routing

Use `examples/README.md` as the authoritative module list. Current modules:

| User scenario                         | Example module                | Start reading at                                                                       |
|---------------------------------------|-------------------------------|----------------------------------------------------------------------------------------|
| REST chat / all operation types       | `basic-example`               | `ChatService.java` → `ToolService.java` → `AgentService.java` → `WorkflowService.java` |
| Voice assistant (ASR → LLM → TTS)     | `asr-example`                 | `VoiceTurnService.java` → `LlmService.java` → `AsrTranscriptionService.java`           |
| **Smart glasses / wearable voice AI** | `asr-example` (primary today) | `VoiceTurnService.java` — see Smart Glasses section below                              |
| Multimodal audio upload to SLS        | `asr-example`                 | `application.yml` multimodal section + `GenAiConfig.java`                              |
| Spring Boot bootstrap pattern         | `example-common`              | `GenAiConfig.java`                                                                     |

> New scenario-specific examples (e.g. `smart-glasses-example`) may be added under `examples/`
> at any time. Always re-scan `examples/` before answering — do not assume the table above is complete.

## Smart Glasses / Wearable Voice AI

Smart glasses backends typically share the **voice turn pipeline**: microphone audio in,
ASR, LLM reasoning, optional tool calls, TTS/audio out — often over WebSocket or streaming RPC.

### Reference implementation (today)

Use **`examples/asr-example`** as the canonical pattern until a dedicated smart-glasses
example is added:

```
websocket.session                          # device/session channel (VoiceSessionTelemetry)
└─ invoke_workflow voice_assistant_turn    # one user utterance turn
   ├─ generate_content {asr-model}         # mic audio in (BlobPart PCM)
   ├─ chat {llm-model}                     # intent + reply
   ├─ execute_tool {name}                  # optional device/world actions
   └─ generate_content {tts-model}         # audio out (BlobPart WAV, output.type=speech)
```

**Key classes to study and adapt:**

| Class                     | Role for smart glasses                             |
|---------------------------|----------------------------------------------------|
| `VoiceTurnService`        | Turn-level workflow orchestration — **start here** |
| `VoiceSessionTelemetry`   | Long-lived device/WebSocket session span           |
| `AsrWebSocketHandler`     | Streaming audio ingress                            |
| `AsrTranscriptionService` | ASR span lifecycle + streaming metrics             |
| `LlmService`              | Intent classification + reply `chat` spans         |
| `TtsSynthesisService`     | TTS `generate_content` with audio output           |
| `CallbackStreamMetrics`   | Streaming time-to-first-chunk metrics              |
| `GenAiConfig`             | OTel + multimodal SLS credential bridging          |

**Smart-glasses-specific adaptations** (when no dedicated example exists):

- Replace WebSocket with the device's transport (gRPC stream, MQTT, BLE gateway) but keep
  the same span hierarchy: session span → workflow turn → ASR/LLM/TTS children.
- Set `gen_ai.conversation.id` on workflow and inference spans for per-user session tracking.
- For camera/vision input, use `BlobPart` with image MIME types in `generate_content` spans.
- Enable multimodal upload when audio/image blobs are too large for span attributes
  (see `asr-example/README.md` → Multimodal blob upload).

### When a dedicated smart-glasses example appears

If `examples/` contains a module matching `smart-glasses`, `glasses`, `ar-`, or `wearable`:

1. Read that module's `README.md` first.
2. Use its trace model and key classes instead of `asr-example`.
3. Mention to the user that the repo provides an official smart-glasses reference.

## User Onboarding Workflow

### 1. Gather Context

| Parameter             | Why                                                            |
|-----------------------|----------------------------------------------------------------|
| Application type      | REST, WebSocket voice, smart glasses, batch pipeline           |
| LLM provider          | DashScope, OpenAI, custom                                      |
| Span types needed     | chat, tool, agent, workflow, retrieval, generate_content       |
| Observability backend | Any OTLP-compatible backend (collector, Jaeger, Grafana, etc.) |
| JDK version           | Library: Java 8+; examples: Java 17+                           |

### 2. Match to Example + Guide Code Changes

1. Discover examples (Step 0 above).
2. Show the user the matching example's trace model diagram from its README.
3. Walk through the example's `*Service.java` files and map each span to their business logic.
4. Provide concrete code snippets adapted from the example, not generic pseudocode.

### 3. Add Maven Dependency

```xml

<dependency>
    <groupId>com.alibaba.loongsuite</groupId>
    <artifactId>otel-util-genai</artifactId>
    <version>${otel-util-genai.version}</version>
</dependency>
```

Plus OTel SDK + OTLP exporter. See `README.md` for full Maven/Gradle blocks.
Use latest version from [Maven Central](https://central.sonatype.com/artifact/com.alibaba.loongsuite/otel-util-genai).

### 4. Configure OTLP Export

`otel-util-genai` requires the OpenTelemetry SDK and an OTLP exporter. Use standard
OTel environment variables — compatible with **any OTLP backend**:

```bash
export OTEL_SERVICE_NAME=my-genai-app
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
# Optional: backend-specific auth headers
export OTEL_EXPORTER_OTLP_HEADERS="Authorization=Bearer <token>"
```

Recommended GenAI capture settings:

```bash
export OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental
export OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT=span_and_event
export OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT=true
```

See `examples/basic-example/src/main/resources/application.yml` for the YAML structure.
Adapt OTLP endpoint and headers to the user's backend.

### 5. Verify

- Traces and metrics arrive at the user's OTLP backend.
- Span hierarchy matches the reference example's trace model.
- Message content visible when capture mode is enabled and messages are set before span close.
- Error spans use `inv.fail(e)` or `*Run()` callbacks (Java try-with-resources does NOT auto-fail).

## Well-Known Operation Types

| Operation          | Factory method                                             | Span name                  |
|--------------------|------------------------------------------------------------|----------------------------|
| `chat`             | `handler.inference(provider, model)`                       | `chat {model}`             |
| `generate_content` | `handler.inference(..., "generate_content")`               | `generate_content {model}` |
| `embeddings`       | `handler.embedding(provider, model)`                       | `embeddings {model}`       |
| `execute_tool`     | `handler.tool(name)`                                       | `execute_tool {name}`      |
| `invoke_agent`     | `handler.invokeLocalAgent(...)` / `invokeRemoteAgent(...)` | `invoke_agent {name}`      |
| `invoke_workflow`  | `handler.workflow(name)`                                   | `invoke_workflow {name}`   |
| `retrieval`        | `handler.retrieval(provider, dataSourceId, ...)`           | `retrieval {dataSourceId}` |
| `create_agent`     | `handler.createAgent(...)`                                 | `create_agent {name}`      |

Each operation type has a working implementation in `basic-example`. Voice/multimodal
operations are in `asr-example`.

## Error Handling (Java-Specific)

Java `try-with-resources` does **not** auto-call `fail()` on exceptions.

```java
// Recommended: *Run() callbacks
handler.inferenceRun("openai","gpt-4o",inv ->{
        inv.

setInputMessages(messages);
});

// Or explicit fail
        }catch(
Exception e){
        inv.

fail(e);
    throw e;
}
```

See `examples/basic-example/.../ChatService.java` for the try-with-resources pattern.

## AI Agent Behavior Rules

1. **Always scan `examples/` first** — treat it as living documentation.
2. **Cite example file paths** when guiding code changes.
3. **Stay in scope** — manual GenAI instrumentation with `otel-util-genai` only.
4. **Instrument all relevant spans** — chat, tool, agent, workflow, retrieval as needed.
5. **Smart glasses** → start from `asr-example` unless a dedicated example exists.
6. **Re-check `examples/`** on each new conversation — modules may have been added since last visit.
7. Use **English** unless the user writes in Chinese.

## Related Files

| Document                | Path                                   |
|-------------------------|----------------------------------------|
| Examples index (living) | `examples/README.md`                   |
| API guide               | `docs/USAGE.md`                        |
| Configuration reference | [reference.md](reference.md)           |
| Examples learning guide | [examples-guide.md](examples-guide.md) |
