# basic-example

REST API 示例，演示 OTel GenAI Semantic Conventions v1.41.1 中全部 well-known `gen_ai.operation.name`。

## 运行

```bash
# 编辑 src/main/resources/application.yml — 替换 <your-dashscope-api-key> 及 CMS 占位符

cd examples/basic-example
mvn spring-boot:run
```

## 端点

| 端点 | operation.name |
|------|----------------|
| `POST /api/chat` | `chat` |
| `POST /api/embedding` | `embeddings` |
| `POST /api/tool` | `execute_tool` |
| `POST /api/agent` | `invoke_agent` |
| `POST /api/workflow` | `invoke_workflow` |
| `POST /api/retrieval` | `retrieval` |
| `POST /api/create-agent` | `create_agent` |

### 示例

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello"}'
```

配置见 `src/main/resources/application.yml`。

## otel-util-genai 必填配置

| 配置项 | 说明 | 环境变量 |
|--------|------|----------|
| `otel.semconv.stability.opt.in` | 启用 GenAI 实验语义 | `OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental` |
| `otel.instrumentation.genai.capture.message.content` | 消息内容采集模式 | `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT=span_and_event` |
| `otel.instrumentation.genai.emit.event` | 推荐 `true`，写入 OTel Log Event | `OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT=true` |

### 接入阿里云 CMS/ARMS GenAI 视图

导出 OTLP 到 CMS 时，`OTEL_RESOURCE_ATTRIBUTES` 须包含：

```
gen_ai.instrumentation.sdk.name=loongsuite-genai-utils
acs.arms.service.feature=genai_app
acs.cms.workspace=<your-workspace-id>
```

详见 `application.yml` 中的注释。
