# LoongSuite GenAI Examples

Multi-module examples for `otel-util-genai` manual instrumentation (OTel GenAI Semantic Conventions v1.41.1).

| Module | Description | Port |
|--------|-------------|------|
| [example-common](example-common/) | Shared OTel/GenAI config and voice telemetry helpers | — |
| [basic-example](basic-example/) | REST API covering all well-known `gen_ai.operation.name` values | 8080 |
| [asr-example](asr-example/) | WebSocket ASR → LLM → TTS voice assistant | 8080 |

## Prerequisites

- Java 17+
- Maven 3.8+
- DashScope API Key (China North 2 / Beijing): [Get API Key](https://help.aliyun.com/zh/model-studio/get-api-key)

## Configuration (local demo)

Edit placeholders in each module's `src/main/resources/application.yml` (`<your-dashscope-api-key>`, `<your-license-key>`, `<your-workspace-id>`, etc.). Do not commit real API keys.

## Default DashScope settings (`application.yml`)

| Property / env | Default | Description |
|----------------|---------|-------------|
| `genai.api-key` | `<your-dashscope-api-key>` | DashScope API Key |
| `genai.base-url` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | Beijing OpenAI-compatible endpoint |
| `genai.model` | `qwen-plus` | LLM Chat |
| `dashscope.asr.model` | `fun-asr-realtime` | Real-time ASR |
| `dashscope.tts.model` | `cosyvoice-v3-plus` | Real-time TTS |
| `dashscope.tts.voice` | `longanyang` | v3 system voice |

## Build

```bash
# From repo root
mvn install -DskipTests
mvn -pl examples/example-common,examples/basic-example,examples/asr-example -am compile
```

## basic-example (REST)

Demonstrates `chat`, `embeddings`, `execute_tool`, `invoke_agent`, `invoke_workflow`, `retrieval`, `create_agent`.

```bash
# Edit genai.api-key and CMS placeholders in src/main/resources/application.yml

cd examples/basic-example
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is OpenTelemetry?"}'
```

See [basic-example/README.md](basic-example/README.md) for all endpoints.

## asr-example (WebSocket voice)

End-to-end voice turn with GenAI semconv:

```
websocket.session
└─ invoke_workflow voice_assistant_turn
   ├─ generate_content fun-asr-realtime   # ASR (InferenceInvocation)
   ├─ chat qwen-plus                      # intent + reply
   ├─ execute_tool get_weather            # weather intent only
   └─ generate_content cosyvoice-v3-plus  # TTS + gen_ai.output.type=speech
```

```bash
# Edit genai.api-key and CMS placeholders in src/main/resources/application.yml

cd examples/asr-example && mvn spring-boot:run
```

For WebSocket protocol, test client, and sample utterances, see [asr-example/README.md](asr-example/README.md).

```bash
# In another terminal: send 16 kHz mono WAV
pip install websocket-client
python examples/asr-example/scripts/ws_voice_client.py your-16k-mono.wav
```

Connect: `ws://localhost:8080/ws/asr` → binary PCM → text `END` → receive JSON + MP3.

## CMS / OTLP

`application.yml` keeps full OTLP defaults with **placeholders**. Replace them with your CMS values in the CMS console.

## Project layout

```
examples/
├── pom.xml                 # aggregator
├── example-common/         # GenAiConfig, GenAiOperations, VoiceSessionTelemetry, ...
├── basic-example/          # REST Spring Boot app
└── asr-example/            # WebSocket voice Spring Boot app
```
