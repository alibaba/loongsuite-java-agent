# asr-example

WebSocket 语音助手 demo，使用 **otel-util-genai** 手动埋点，对齐 GenAI semconv v1.41.1。

本模块**只有后端**，没有内置浏览器页面；需用 WebSocket 客户端发送 PCM 音频（见下方「如何使用」）。

## 如何使用

### 1. 准备环境

- JDK **17+**、Maven **3.8+**
- 百炼 API Key（华北2 北京）：[获取 API Key](https://help.aliyun.com/zh/model-studio/get-api-key)
- 测试客户端：本目录 `scripts/ws_voice_client.py`（需 `pip install websocket-client`）

### 2. 配置并启动服务

```bash
# 在仓库根目录 — 先编辑 src/main/resources/application.yml
cd examples/asr-example
mvn spring-boot:run
```

服务监听 **8080**（可在 `application.yml` 修改），WebSocket 路径 **`/ws/asr`**。

成功启动后日志中可见 Spring Boot 与 `Tomcat started on port 8080`。

### 3. 发送语音（命令行测试）

准备 **16kHz、单声道、16-bit PCM** 的 WAV（可用 ffmpeg 转换）：

```bash
# 示例：从任意音频转成 demo 所需格式
ffmpeg -i your-voice.m4a -ar 16000 -ac 1 examples/asr-example/scripts/sample.wav

pip install websocket-client
python examples/asr-example/scripts/ws_voice_client.py examples/asr-example/scripts/sample.wav
```

或管道输入 PCM：

```bash
ffmpeg -i your-voice.m4a -ar 16000 -ac 1 -f s16le - \
  | python examples/asr-example/scripts/ws_voice_client.py --stdin
```

### 4. 一次完整交互流程

```
客户端                          服务端 (asr-example)
  |                                  |
  |---- WebSocket connect ---------->|  返回 {"type":"connected",...}
  |---- binary: PCM 音频块 (可多次) ->|  流式送入 fun-asr-realtime
  |---- text: END ------------------>|  触发 ASR → LLM → TTS
  |<--- {"type":"transcript",...} ---|  识别文本
  |<--- {"type":"intent",...} -------|  weather / chitchat
  |<--- {"type":"text",...} ---------|  LLM 回复文本
  |<--- binary: WAV 音频块 (可多次) --|  cosyvoice 流式 TTS
  |<--- {"type":"complete"} ---------|  本轮结束
```

**客户端约定**

| 方向 | 类型 | 内容 |
|------|------|------|
| → 服务端 | Binary | PCM，16kHz mono s16le，建议每包 ~100ms |
| → 服务端 | Text | 固定字符串 `END`（表示本轮说完，开始处理） |
| ← 服务端 | Text JSON | `connected` / `transcript` / `intent` / `text` / `complete` / `error` |
| ← 服务端 | Binary | TTS 音频（WAV 片段） |

### 5. 建议测试话术

| 说法 | 预期 intent | Trace 亮点 |
|------|-------------|--------------|
| 今天杭州天气是什么 | `weather` | `execute_tool get_weather` + 两次 `chat` |
| 你好 | `chitchat` | 普通闲聊 + TTS |

**推荐测试音频**：语音备忘录录「今天杭州天气是什么」，转成 16kHz WAV 后：

```bash
afconvert -f WAVE -d LEI16@16000 ask-weather.m4a ask-weather-16k.wav
python scripts/ws_voice_client.py ask-weather-16k.wav --url ws://localhost:8080/ws/asr
```

### 6. 查看 Trace

默认通过 **OTLP**（`http/protobuf`）上报到 CMS APM。将 `application.yml` 中的 `<your-*>` 占位符替换为你的 CMS 配置。配置见 `application.yml` 中 `otel.exporter.otlp.*`。

Trace 结构见下文「Trace model」。

---

## 百炼配置（华北2 北京）

| 能力 | 环境变量 | 默认值 | 百炼文档 |
|------|----------|--------|----------|
| API Key | `application.yml` 中 `genai.api-key` | `<your-dashscope-api-key>` | [获取 API Key](https://help.aliyun.com/zh/model-studio/get-api-key) |
| LLM Chat | `GENAI_MODEL` | `qwen-plus` | [首次调用千问](https://help.aliyun.com/zh/model-studio/first-api-call-to-qwen) |
| LLM Base URL | `GENAI_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 北京 OpenAI 兼容端点 |
| 实时 ASR | `DASHSCOPE_ASR_MODEL` | `fun-asr-realtime` | 实时语音识别 |
| 实时 TTS | `DASHSCOPE_TTS_MODEL` | `cosyvoice-v3-plus` | [语音合成模型选型 doc 3026935](https://help.aliyun.com/document_detail/3026935.html) |
| TTS 音色 | `DASHSCOPE_TTS_VOICE` | `longanyang` | [CosyVoice Java SDK](https://help.aliyun.com/zh/model-studio/cosyvoice-tts-java-sdk) |

> **模型与音色须同版本**：`cosyvoice-v3-plus` 用 `longanyang` 等 v3 音色；`cosyvoice-v2` 才用 `longxiaochun_v2`。

## otel-util-genai 必填配置

使用 **otel-util-genai** 手动埋点时，`application.yml` 中至少需要：

| 配置项 | 说明 | 环境变量 |
|--------|------|----------|
| `otel.semconv.stability.opt.in` | 启用 GenAI 实验语义（否则 content capture 仅支持 true/false） | `OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental` |
| `otel.instrumentation.genai.capture.message.content` | 消息内容采集模式 | `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT=span_and_event` |
| `otel.instrumentation.genai.emit.event` | 推荐 `true`，配合 event 模式 | `OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT=true` |

### 接入阿里云 CMS/ARMS GenAI 视图

导出 OTLP 到 CMS 时，除 `OTEL_EXPORTER_OTLP_HEADERS`（`x-arms-license-key`、`x-arms-project`、`x-cms-workspace`）外，`OTEL_RESOURCE_ATTRIBUTES` **须包含**：

```
gen_ai.instrumentation.sdk.name=loongsuite-genai-utils
acs.arms.service.feature=genai_app
acs.cms.workspace=<your-workspace-id>
```

详见 `src/main/resources/application.yml` 中的注释块。

## Pipeline（内部逻辑）

1. WebSocket 连接 `ws://localhost:8080/ws/asr`
2. 客户端发送 PCM 二进制帧
3. 客户端发送文本 `END`
4. **ASR** → **LLM 意图** (`chat`) → **LLM 回复** (`chat`) → **TTS**（weather 意图时调用 **execute_tool get_weather**）
5. 返回 JSON 事件 + WAV 二进制流

## Trace model

| Span | Component | `gen_ai.operation.name` |
|------|-----------|-------------------------|
| `websocket.session` | OTel Tracer (INTERNAL) | — |
| `invoke_workflow voice_assistant_turn` | WorkflowInvocation | `invoke_workflow` |
| `generate_content fun-asr-realtime` | InferenceInvocation | `generate_content` |
| `chat qwen-plus` | InferenceInvocation | `chat` |
| `execute_tool get_weather` | ToolInvocation | `execute_tool` *(weather only)* |
| `generate_content cosyvoice-v3-plus` | InferenceInvocation | `generate_content` + `gen_ai.output.type=speech` |

> ASR/TTS 使用 `InferenceInvocation`，`operation.name=generate_content`；文本用 `TextPart`，音频用 `BlobPart`。**多模态外置上传为可选能力**，仅在配置 `multimodal.storage.base.path` 且 upload mode 非 `none` 时生效（见下文）。

### 多模态 blob 上传（可选）

**不配置也能正常产生 GenAI span。** 业务代码用 `BlobPart` 传递音频；配置齐全后，`otel-util-genai` 自动注册 `MultimodalCompletionHook`，在 span 结束前把 blob 上传到 SLS（或本地目录），并替换为 `UriPart`。CMS GenAI 视图可通过 metadata 中的 `sls://…` URI 播放音频。

#### 本 demo 会上传什么

| Span | 方向 | span 内音频 | 上传后 |
|------|------|-------------|--------|
| `generate_content fun-asr-realtime` | input | PCM（`audio/pcm`） | 开启 `multimodal.audio.conversion` 时为 `.wav`，否则 `.pcm` |
| `generate_content cosyvoice-v3-plus` | output | WAV（`audio/wav`） | `.wav` |

TTS 使用 `WAV_22050HZ_MONO_16BIT`，便于 CMS 渲染。ASR 输入 PCM 可通过 `multimodal.audio.conversion` 在上传时转为 WAV。

`BlobPart` 的 **modality 会从 MIME 自动推断**（`audio/wav` → `audio`），一般只需传 MIME + 字节：

```java
new BlobPart("audio/wav", wavBytes);
new BlobPart("audio/pcm", pcmBytes);
```

#### 前置条件（上传 + CMS metadata 均需满足）

| 配置项 | 值 | 环境变量 |
|--------|-----|----------|
| `otel.semconv.stability.opt.in` | `gen_ai_latest_experimental` | `OTEL_SEMCONV_STABILITY_OPT_IN` |
| `otel.instrumentation.genai.capture.message.content` | `span_and_event`（或 `span_only`） | `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT` |
| `otel.instrumentation.genai.extended.enabled` | `true`（默认） | `OTEL_INSTRUMENTATION_GENAI_EXTENDED_ENABLED` |
| `otel.instrumentation.genai.multimodal.upload.mode` | `input` / `output` / `both` | `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOAD_MODE` |
| `otel.instrumentation.genai.multimodal.storage.base.path` | `sls://project/logstore` | `OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH` |

`GenAiConfig`（example-common）会在 OTel SDK 初始化前，把 `application.yml` 中的 `alibaba.cloud.sls.*` 桥接到 `ALIBABA_CLOUD_*` 系统属性。

#### 示例：启用 SLS 上传

编辑 `src/main/resources/application.yml`（仓库内用占位符，真实密钥走环境变量）：

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
  multimodal.audio.conversion: true       # PCM → WAV（CMS 可播放）
```

或通过环境变量覆盖：

```bash
export ALIBABA_CLOUD_SLS_ENDPOINT=https://cn-hangzhou.log.aliyuncs.com
export ALIBABA_CLOUD_ACCESS_KEY_ID=<your-ak>
export ALIBABA_CLOUD_ACCESS_KEY_SECRET=<your-sk>
export OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH=sls://my-project/my-logstore
export OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOAD_MODE=both
export OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOADER=sls
export OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_AUDIO_CONVERSION=true
```

需要 classpath 上有 **aliyun-log SDK ≥ 0.6.155**（由 `otel-util-genai` 传递依赖引入）。

#### 对象 URI 规则

```
sls://{project}/{logstore}/{yyyyMMdd}/{md5}.{ext}
```

示例：`sls://my-project/my-logstore/20260703/eaab5db4….wav`

请从 **CMS trace** 复制完整 URI（`gen_ai.output.multimodal_metadata` 或 message 中的 `UriPart`），不要仅凭 MD5 文件名拼接。

#### CMS metadata 格式

上传后 span metadata 条目形如（snake_case，与 Python agent 一致）：

```json
{
  "type": "uri",
  "mime_type": "audio/wav",
  "modality": "audio",
  "uri": "sls://project/logstore/20260703/abc123.wav"
}
```

若 CMS 无法播放，检查 `modality` 是否为 `audio`（不是 `speech`），字段名是否为 `mime_type`（不是 `mimeType`）。

#### 验证上传（脚本）

**Round-trip 冒烟**（PutObject + GetObject）：见 `scripts/SlsMultimodalRoundTrip.java`。

**从 SLS 下载对象**（URI 从 CMS trace 复制）：

```bash
cd examples/asr-example/scripts
python3 -m venv .venv-sls && source .venv-sls/bin/activate
pip install aliyun-log-python-sdk pyyaml
python get_sls_object.py \
  'sls://project/logstore/20260703/abc123.wav' \
  /tmp/out.wav
```

默认读取 `../src/main/resources/application.yml` 中的 `alibaba.cloud.sls.*`。Java CLI：`scripts/GetSlsMultimodalObject.java`。

#### 关闭上传

设置 `multimodal.upload.mode: none`，或删除 `multimodal.storage.base.path`。span 仍记录内联 `BlobPart`，不会外置存储。

#### 多模态故障排查

| 现象 | 处理 |
|------|------|
| span 上没有 `gen_ai.*.multimodal_metadata` | 确认 `capture.message.content`、`extended.enabled` 已开，且 `multimodal.upload.mode` ≠ `none` |
| 日志：`Falling back to local multimodal uploader` | SLS 初始化失败 — 检查 AK/SK、endpoint、aliyun-log 版本 |
| GetObject 404 | 对象不在 SLS（fallback 写到本地 `sls:/…`）或 URI 错误（缺少日期前缀） |
| CMS 有 URI 但无播放器 | 使用 `audio/wav` + `modality: audio`；ASR PCM 需开 `multimodal.audio.conversion` |

## 故障排查

| 现象 | 处理 |
|------|------|
| 启动报 `api-key is not set` | 在 `application.yml` 替换 `<your-dashscope-api-key>`，或 export `GENAI_API_KEY` / `DASHSCOPE_API_KEY` |
| WebSocket 连不上 | 确认端口 8080、路径 `/ws/asr` |
| `未能识别语音内容` | 确认 PCM 为 16kHz mono；WAV 需先 ffmpeg 转换 |
| TTS 报错 | 检查模型/音色版本是否匹配（v3 模型 + v3 音色） |
| 多模态未上传 | 见 [多模态 blob 上传](#多模态-blob-上传可选) |
| CMS 音频无法播放 | TTS 已输出 WAV；ASR PCM 需 `multimodal.audio.conversion: true` |

## Key classes

- `ws/AsrWebSocketHandler` — WebSocket 协议入口
- `service/VoiceTurnService` — Workflow + span 编排
- `service/LlmService` — 意图分类 + 回复（`chat`）
- `service/WeatherToolService` — `execute_tool get_weather`
- `scripts/ws_voice_client.py` — 命令行测试客户端
- `scripts/get_sls_object.py` — 从 SLS 下载多模态对象（GetObject）
- `scripts/GetSlsMultimodalObject.java` — Java 版 GetObject CLI
- `example-common/GenAiConfig` — 将 `otel.*`、`alibaba.cloud.sls.*` 桥接到系统属性
- `example-common/GenAiOperations` — 标准 `gen_ai.operation.name` 常量
