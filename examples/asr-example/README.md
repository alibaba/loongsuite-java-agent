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
  |<--- binary: WAV chunks ----------|  cosyvoice streaming TTS
  |<--- {"type":"complete"} ---------|  turn finished
```

**Client protocol**

| Direction | Type | Content |
|-----------|------|---------|
| → Server | Binary | PCM, 16 kHz mono s16le, ~100 ms per chunk recommended |
| → Server | Text | Literal `END` (end of utterance, start processing) |
| ← Server | Text JSON | `connected` / `transcript` / `intent` / `text` / `complete` / `error` |
| ← Server | Binary | TTS audio (WAV fragments) |

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
5. Server returns JSON events + WAV binary stream

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

GenAI spans work **without** external upload. Application code passes audio as `BlobPart`; when multimodal is configured, `otel-util-genai` auto-registers `MultimodalCompletionHook`, uploads blobs to SLS (or local disk), and replaces them with `UriPart` before the span ends. CMS GenAI view can then play audio from the `sls://…` URI in span metadata.

#### What gets uploaded in this demo

| Span | Direction | Audio in span | After upload |
|------|-----------|---------------|--------------|
| `generate_content fun-asr-realtime` | input | PCM (`audio/pcm`) | `.wav` (default: PCM→WAV on upload) |
| `generate_content cosyvoice-v3-plus` | output | WAV (`audio/wav`) | `.wav` |

TTS uses `WAV_22050HZ_MONO_16BIT` so CMS can render playback. ASR input PCM is converted to WAV on upload by default (`multimodal.audio.conversion` defaults to `true` in the library). Set `multimodal.audio.conversion: false` to upload raw PCM.

`BlobPart` **modality is inferred** from MIME type (`audio/wav` → `audio`). You usually only pass MIME + bytes:

```java
new BlobPart("audio/wav", wavBytes);
new BlobPart("audio/pcm", pcmBytes);
```

#### Prerequisites (all required for upload + CMS metadata)

| Property | Value | Env var |
|----------|-------|---------|
| `otel.semconv.stability.opt.in` | `gen_ai_latest_experimental` | `OTEL_SEMCONV_STABILITY_OPT_IN` |
| `otel.instrumentation.genai.capture.message.content` | `span_and_event` (or `span_only`) | `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT` |
| `otel.instrumentation.genai.extended.enabled` | `true` (default) | `OTEL_INSTRUMENTATION_GENAI_EXTENDED_ENABLED` |
| `otel.instrumentation.genai.multimodal.upload.mode` | `input` / `output` / `both` | `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOAD_MODE` |
| `otel.instrumentation.genai.multimodal.storage.base.path` | `sls://project/logstore` | `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH` |

`GenAiConfig` (example-common) bridges `alibaba.cloud.sls.*` from `application.yml` to `ALIBABA_CLOUD_*` system properties before OTel SDK init.

#### Example: enable SLS upload

Edit `src/main/resources/application.yml` (use placeholders in git; real keys via env vars):

```yaml
alibaba:
  cloud:
    sls:
      endpoint: ${ALIBABA_CLOUD_SLS_ENDPOINT:https://cn-hangzhou.log.aliyuncs.com}
      access-key-id: ${ALIBABA_CLOUD_ACCESS_KEY_ID:<your-access-key-id>}
      access-key-secret: ${ALIBABA_CLOUD_ACCESS_KEY_SECRET:<your-access-key-secret>}

otel.instrumentation.genai:
  capture.message.content: span_and_event
  multimodal.upload.mode: both          # input + output
  multimodal.storage.base.path: sls://<project>/<logstore>
  multimodal.uploader: sls
```

Or override via environment:

```bash
export ALIBABA_CLOUD_SLS_ENDPOINT=https://cn-hangzhou.log.aliyuncs.com
export ALIBABA_CLOUD_ACCESS_KEY_ID=<your-ak>
export ALIBABA_CLOUD_ACCESS_KEY_SECRET=<your-sk>
export OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH=sls://my-project/my-logstore
export OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOAD_MODE=both
export OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOADER=sls
```

Requires **aliyun-log SDK ≥ 0.6.155** on the classpath (pulled transitively by `otel-util-genai`).

#### Object URI layout

Uploaded objects use:

```
sls://{project}/{logstore}/{yyyyMMdd}/{md5}.{ext}
```

Example: `sls://my-project/my-logstore/20260703/eaab5db4….wav`

Use the **full URI from CMS trace** (`gen_ai.output.multimodal_metadata` or message `UriPart`). Do not guess from MD5 filename alone.

#### CMS metadata shape

After upload, span metadata entries look like (snake_case, aligned with Python agent):

```json
{
  "type": "uri",
  "mime_type": "audio/wav",
  "modality": "audio",
  "uri": "sls://project/logstore/20260703/abc123.wav"
}
```

If CMS does not render audio, check `modality` is `audio` (not `speech`) and keys use `mime_type` (not `mimeType`).

#### Verify upload (scripts)

**Round-trip smoke test** (PutObject + GetObject):

```bash
# From repo root, after mvn install
export ALIBABA_CLOUD_SLS_ENDPOINT=...
export ALIBABA_CLOUD_ACCESS_KEY_ID=...
export ALIBABA_CLOUD_ACCESS_KEY_SECRET=...
# compile + run SlsMultimodalRoundTrip (see scripts/SlsMultimodalRoundTrip.java)
```

**Download object from SLS** (copy URI from CMS trace):

```bash
cd examples/asr-example/scripts
python3 -m venv .venv-sls && source .venv-sls/bin/activate
pip install aliyun-log-python-sdk pyyaml
python get_sls_object.py \
  'sls://project/logstore/20260703/abc123.wav' \
  /tmp/out.wav
```

Reads `alibaba.cloud.sls.*` from `../src/main/resources/application.yml` by default. Java CLI: `scripts/GetSlsMultimodalObject.java`.

#### Disable upload

Set `multimodal.upload.mode: none`, or remove `multimodal.storage.base.path`. Spans still record inline `BlobPart` without external storage.

#### Multimodal troubleshooting

| Symptom | Fix |
|---------|-----|
| No `gen_ai.*.multimodal_metadata` on span | Enable `capture.message.content`, `extended.enabled`, and `multimodal.upload.mode` ≠ `none` |
| Log: `Falling back to local multimodal uploader` | SLS init failed — check AK/SK, endpoint, aliyun-log version |
| GetObject 404 | Object not on SLS (local fallback wrote under `sls:/…` in repo cwd) or wrong URI (missing date prefix) |
| CMS shows URI but no player | Use `audio/wav` + `modality: audio`; ASR PCM→WAV is on by default — check you did not set `multimodal.audio.conversion: false` |

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Startup: `api-key is not set` | Replace `<your-dashscope-api-key>` in `application.yml`, or export `GENAI_API_KEY` / `DASHSCOPE_API_KEY` |
| WebSocket connection failed | Check port 8080 and path `/ws/asr` |
| `未能识别语音内容` / no speech recognized | Ensure PCM is 16 kHz mono; convert WAV with ffmpeg |
| TTS error | Match model and voice version (v3 model + v3 voice) |
| Multimodal not uploading | See [Multimodal blob upload](#multimodal-blob-upload-optional) |
| CMS audio not playable | TTS outputs WAV; ASR PCM→WAV is on by default — verify upload produced `.wav`, not `.pcm` |

## Key classes

- `ws/AsrWebSocketHandler` — WebSocket entry
- `service/VoiceTurnService` — workflow and span orchestration
- `service/LlmService` — intent classification + reply (`chat`)
- `service/WeatherToolService` — `execute_tool get_weather`
- `scripts/ws_voice_client.py` — CLI test client
- `scripts/get_sls_object.py` — download multimodal object from SLS (GetObject)
- `scripts/GetSlsMultimodalObject.java` — Java GetObject CLI
- `example-common/GenAiConfig` — bridges `otel.*` and `alibaba.cloud.sls.*` to system properties
- `example-common/GenAiOperations` — standard `gen_ai.operation.name` constants
