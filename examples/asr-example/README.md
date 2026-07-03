# asr-example

WebSocket voice assistant demo using **otel-util-genai** manual instrumentation, aligned with GenAI semconv v1.41.1.

This module is **backend only** — no built-in browser UI. Send PCM audio via a WebSocket client (see [How to use](#how-to-use)).

## How to use

### 1. Prerequisites

- JDK **17+**, Maven **3.8+**
- DashScope API Key (China North 2 / Beijing): [Get API Key](https://help.aliyun.com/zh/model-studio/get-api-key)
- Test client: `scripts/ws_voice_client.py` in this directory (`pip install websocket-client`)

### 2. Configure and start

```bash
# From repo root — edit src/main/resources/application.yml first
cd examples/asr-example
mvn spring-boot:run
```

The server listens on **8080** (configurable in `application.yml`). WebSocket path: **`/ws/asr`**.

On success, logs show Spring Boot startup and `Tomcat started on port 8080`.

### 3. Send voice (CLI test)

Prepare **16 kHz, mono, 16-bit PCM** WAV (convert with ffmpeg):

```bash
ffmpeg -i your-voice.m4a -ar 16000 -ac 1 examples/asr-example/scripts/sample.wav

pip install websocket-client
python examples/asr-example/scripts/ws_voice_client.py examples/asr-example/scripts/sample.wav
```

Or pipe raw PCM:

```bash
ffmpeg -i your-voice.m4a -ar 16000 -ac 1 -f s16le - \
  | python examples/asr-example/scripts/ws_voice_client.py --stdin
```

### 4. Interaction flow

```
Client                          Server (asr-example)
  |                                  |
  |---- WebSocket connect ---------->|  {"type":"connected",...}
  |---- binary: PCM chunks --------->|  stream to fun-asr-realtime
  |---- text: END ------------------>|  ASR → LLM → TTS
  |<--- {"type":"transcript",...} ---|  recognized text
  |<--- {"type":"intent",...} -------|  weather / chitchat
  |<--- {"type":"text",...} ---------|  LLM reply text
  |<--- binary: MP3 chunks ----------|  cosyvoice streaming TTS
  |<--- {"type":"complete"} ---------|  turn finished
```

**Client protocol**

| Direction | Type | Content |
|-----------|------|---------|
| → Server | Binary | PCM, 16 kHz mono s16le, ~100 ms per chunk recommended |
| → Server | Text | Literal `END` (end of utterance, start processing) |
| ← Server | Text JSON | `connected` / `transcript` / `intent` / `text` / `complete` / `error` |
| ← Server | Binary | TTS audio (MP3 fragments) |

### 5. Sample utterances

| Utterance | Expected intent | Trace highlights |
|-----------|-----------------|------------------|
| 今天杭州天气是什么 | `weather` | `execute_tool get_weather` + two `chat` spans |
| 你好 | `chitchat` | casual chat + TTS |

**Recommended test audio**: record “今天杭州天气是什么”, convert to 16 kHz WAV:

```bash
afconvert -f WAVE -d LEI16@16000 ask-weather.m4a ask-weather-16k.wav
python scripts/ws_voice_client.py ask-weather-16k.wav --url ws://localhost:8080/ws/asr
```

### 6. View traces

Traces are exported via **OTLP** (`http/protobuf`) to CMS APM by default. Replace `<your-*>` placeholders in `application.yml`. See `otel.exporter.otlp.*` in `application.yml`.

See [Trace model](#trace-model) below.

---

## DashScope configuration (Beijing)

| Capability | Env var | Default | Docs |
|------------|---------|---------|------|
| API Key | `genai.api-key` in `application.yml` | `<your-dashscope-api-key>` | [Get API Key](https://help.aliyun.com/zh/model-studio/get-api-key) |
| LLM Chat | `GENAI_MODEL` | `qwen-plus` | [First API call](https://help.aliyun.com/zh/model-studio/first-api-call-to-qwen) |
| LLM Base URL | `GENAI_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | Beijing compatible endpoint |
| Real-time ASR | `DASHSCOPE_ASR_MODEL` | `fun-asr-realtime` | Real-time speech recognition |
| Real-time TTS | `DASHSCOPE_TTS_MODEL` | `cosyvoice-v3-plus` | [TTS model selection](https://help.aliyun.com/document_detail/3026935.html) |
| TTS voice | `DASHSCOPE_TTS_VOICE` | `longanyang` | [CosyVoice Java SDK](https://help.aliyun.com/zh/model-studio/cosyvoice-tts-java-sdk) |

> **Model and voice must match version**: use v3 voices (e.g. `longanyang`) with `cosyvoice-v3-plus`; v2 voices (e.g. `longxiaochun_v2`) require `cosyvoice-v2`.

## otel-util-genai required settings

When using **otel-util-genai** manual instrumentation, `application.yml` must at least include:

| Property | Description | Env var |
|----------|-------------|---------|
| `otel.semconv.stability.opt.in` | Enable GenAI experimental semconv | `OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental` |
| `otel.instrumentation.genai.capture.message.content` | Content capture mode | `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT=span_and_event` |
| `otel.instrumentation.genai.emit.event` | Recommended `true` for event mode | `OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT=true` |

### Alibaba Cloud CMS/ARMS GenAI view

When exporting OTLP to CMS, in addition to `OTEL_EXPORTER_OTLP_HEADERS` (`x-arms-license-key`, `x-arms-project`, `x-cms-workspace`), `OTEL_RESOURCE_ATTRIBUTES` **must include**:

```
gen_ai.instrumentation.sdk.name=loongsuite-genai-utils
acs.arms.service.feature=genai_app
acs.cms.workspace=<your-workspace-id>
```

See the comment block in `src/main/resources/application.yml`.

## Pipeline

1. WebSocket connect `ws://localhost:8080/ws/asr`
2. Client sends PCM binary frames
3. Client sends text `END`
4. **ASR** → **LLM intent** (`chat`) → **LLM reply** (`chat`) → **TTS** (calls **execute_tool get_weather** on weather intent)
5. Server returns JSON events + MP3 binary stream

## Trace model

| Span | Component | `gen_ai.operation.name` |
|------|-----------|-------------------------|
| `websocket.session` | OTel Tracer (INTERNAL) | — |
| `invoke_workflow voice_assistant_turn` | WorkflowInvocation | `invoke_workflow` |
| `generate_content fun-asr-realtime` | InferenceInvocation | `generate_content` |
| `chat qwen-plus` | InferenceInvocation | `chat` |
| `execute_tool get_weather` | ToolInvocation | `execute_tool` *(weather only)* |
| `generate_content cosyvoice-v3-plus` | InferenceInvocation | `generate_content` + `gen_ai.output.type=speech` |

> ASR/TTS use `InferenceInvocation` with `operation.name=generate_content`. Text uses `TextPart`; audio uses `BlobPart` in messages. **External multimodal upload is optional** — enabled only when `multimodal.storage.base.path` and upload mode are configured (see below).

### Multimodal blob upload (optional)

Not required for GenAI spans. Application code always uses `BlobPart` for PCM/MP3; the library's `MultimodalCompletionHook` (auto-registered when configured) uploads blobs and replaces them with `UriPart` before span end.

To **enable** upload, set in `application.yml`:

| Property | Example | Description |
|----------|---------|-------------|
| `otel.instrumentation.genai.multimodal.upload.mode` | `both` | `input` / `output` / `both` / `none` |
| `otel.instrumentation.genai.multimodal.storage.base.path` | `file:///tmp/genai-multimodal` or `sls://project/logstore` | Upload destination |
| `otel.instrumentation.genai.multimodal.uploader` | `local` or `sls` | Uploader implementation (SLS needs AK/SK env vars) |

To **disable** upload: set `multimodal.upload.mode: none`, or omit `multimodal.storage.base.path`. Spans still record `BlobPart` without external storage.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Startup: `api-key is not set` | Replace `<your-dashscope-api-key>` in `application.yml`, or export `GENAI_API_KEY` / `DASHSCOPE_API_KEY` |
| WebSocket connection failed | Check port 8080 and path `/ws/asr` |
| `未能识别语音内容` / no speech recognized | Ensure PCM is 16 kHz mono; convert WAV with ffmpeg |
| TTS error | Match model and voice version (v3 model + v3 voice) |

## Key classes

- `ws/AsrWebSocketHandler` — WebSocket entry
- `service/VoiceTurnService` — workflow and span orchestration
- `service/LlmService` — intent classification + reply (`chat`)
- `service/WeatherToolService` — `execute_tool get_weather`
- `scripts/ws_voice_client.py` — CLI test client
- `example-common/GenAiOperations` — standard `gen_ai.operation.name` constants
