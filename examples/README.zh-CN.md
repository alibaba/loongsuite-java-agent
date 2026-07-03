# LoongSuite GenAI Examples

`otel-util-genai` 手动埋点多模块示例（OTel GenAI Semantic Conventions v1.41.1）。

| 模块 | 说明 | 端口 |
|------|------|------|
| [example-common](example-common/) | 共享 OTel/GenAI 配置与语音遥测辅助类 | — |
| [basic-example](basic-example/) | REST API，覆盖全部 well-known `gen_ai.operation.name` | 8080 |
| [asr-example](asr-example/) | WebSocket 语音助手：ASR → LLM → TTS | 8080 |

## 环境要求

- Java 17+
- Maven 3.8+
- 百炼 API Key（华北2 北京）：[获取 API Key](https://help.aliyun.com/zh/model-studio/get-api-key)

## 配置方式（本地 demo）

直接编辑各模块 `src/main/resources/application.yml` 中的占位符（`<your-dashscope-api-key>`、`<your-license-key>`、`<your-workspace-id>` 等）。切勿提交真实 API Key。

## 百炼默认配置（`application.yml`）

| 配置项 / 环境变量 | 默认值 | 说明 |
|-------------------|--------|------|
| `genai.api-key` | `<your-dashscope-api-key>` | 百炼 API Key |
| `genai.base-url` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 北京 OpenAI 兼容端点 |
| `genai.model` | `qwen-plus` | LLM Chat |
| `dashscope.asr.model` | `fun-asr-realtime` | 实时 ASR |
| `dashscope.tts.model` | `cosyvoice-v3-plus` | 实时 TTS |
| `dashscope.tts.voice` | `longanyang` | v3 系统音色 |

## 构建

```bash
# 在仓库根目录
mvn install -DskipTests
mvn -pl examples/example-common,examples/basic-example,examples/asr-example -am compile
```

## basic-example（REST）

演示 `chat`、`embeddings`、`execute_tool`、`invoke_agent`、`invoke_workflow`、`retrieval`、`create_agent`。

```bash
# 编辑 src/main/resources/application.yml 中的 genai.api-key 与 CMS 占位符

cd examples/basic-example
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is OpenTelemetry?"}'
```

完整端点见 [basic-example/README.zh-CN.md](basic-example/README.zh-CN.md)。

## asr-example（WebSocket 语音）

端到端语音交互 Trace 结构：

```
websocket.session
└─ invoke_workflow voice_assistant_turn
   ├─ generate_content fun-asr-realtime   # ASR（InferenceInvocation）
   ├─ chat qwen-plus                      # 意图识别 + 回复
   ├─ execute_tool get_weather            # weather 意图
   └─ generate_content cosyvoice-v3-plus  # TTS + gen_ai.output.type=speech
```

```bash
# 编辑 src/main/resources/application.yml 中的 genai.api-key 与 CMS 占位符

cd examples/asr-example && mvn spring-boot:run
```

WebSocket 协议、测试客户端与示例话术见 [asr-example/README.zh-CN.md](asr-example/README.zh-CN.md)。

```bash
# 另开终端：发送 16kHz mono WAV
pip install websocket-client
python examples/asr-example/scripts/ws_voice_client.py your-16k-mono.wav
```

连接 `ws://localhost:8080/ws/asr` → 发送 PCM 二进制 → 文本 `END` → 接收 JSON + MP3。

## CMS / OTLP

`application.yml` 保留完整 OTLP 默认结构，敏感项使用**占位符**。请在 CMS 控制台获取对应值并替换。

## 目录结构

```
examples/
├── pom.xml                 # 聚合模块
├── example-common/         # GenAiConfig、GenAiOperations、VoiceSessionTelemetry 等
├── basic-example/          # REST Spring Boot 应用
└── asr-example/            # WebSocket 语音 Spring Boot 应用
```
