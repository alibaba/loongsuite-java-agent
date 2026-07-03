# 使用指南

[English](USAGE.md) | **中文**

`otel-util-genai` 详细用法。安装与 Maven 坐标见 [README.zh-CN.md](../README.zh-CN.md)。

## 快速开始

### 1. 初始化 OpenTelemetry

```bash
export OTEL_SERVICE_NAME=my-genai-app
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

```java
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

var openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
```

### 2. 创建 Handler 并 Instrument 一次 LLM 调用

```java
import com.alibaba.loongsuite.otel.util.genai.*;
import com.alibaba.loongsuite.otel.util.genai.types.*;
import java.util.List;

var handler = GenAiTelemetryHandler.create(openTelemetry);

try (var inv = handler.inference("openai", "gpt-4o")) {
    inv.setInputMessages(List.of(
        new InputMessage("user", List.of(new TextPart("你好")))
    ));
    inv.setTemperature(0.7);

    var response = client.chat(request);

    inv.setOutputMessages(List.of(
        new OutputMessage("assistant",
            List.of(new TextPart(response.content())), "stop")
    ));
    inv.setResponseModel(response.model());
    inv.setInputTokens(response.usage().promptTokens());
    inv.setOutputTokens(response.usage().completionTokens());
}
```

## 操作类型

| 操作 | 工厂方法 | Span 名称 | SpanKind |
|---|---|---|---|
| LLM 推理 | `handler.inference("openai", "gpt-4o")` | `chat gpt-4o` | CLIENT |
| Embedding | `handler.embedding("openai", "text-embedding-3-small")` | `embeddings text-embedding-3-small` | CLIENT |
| Tool 执行 | `handler.tool("get_weather")` | `execute_tool get_weather` | INTERNAL |
| 本地 Agent | `handler.invokeLocalAgent("openai", "gpt-4o", "research")` | `invoke_agent research` | INTERNAL |
| 远程 Agent | `handler.invokeRemoteAgent("openai", "gpt-4o", "code", "a.co", 443)` | `invoke_agent code` | CLIENT |
| Workflow | `handler.workflow("pipeline")` | `invoke_workflow pipeline` | INTERNAL |
| Retrieval | `handler.retrieval("openai", "kb-01", "api.example.com", 443)` | `retrieval kb-01` | CLIENT |
| Create Agent | `handler.createAgent("openai", "gpt-4o", "research-bot", "api.example.com", 443)` | `create_agent research-bot` | CLIENT |

## 分操作示例

### Embedding

```java
try (var inv = handler.embedding("openai", "text-embedding-3-small")) {
    inv.setEncodingFormats(List.of("float"));
    var resp = client.embeddings(request);
    inv.setResponseModel(resp.model());
    inv.setInputTokens(resp.usage().totalTokens());
    inv.setDimensionCount((long) resp.data().get(0).embedding().length);
}
```

### Tool 执行

```java
try (var inv = handler.tool("get_weather", "call_001", "function", "获取天气")) {
    inv.setArguments("{\"city\": \"北京\"}");
    var result = weatherService.getWeather("北京");
    inv.setToolResult(result);
}
```

### Agent

```java
try (var inv = handler.invokeLocalAgent("openai", "gpt-4o", "research-agent")) {
    inv.setAgentId("agent-001");
    inv.setInputMessages(List.of(
        new InputMessage("user", List.of(new TextPart("调研量子计算")))
    ));
    var result = agent.run(task);
    inv.setOutputMessages(List.of(
        new OutputMessage("assistant", List.of(new TextPart(result.text())), "stop")
    ));
    inv.setInputTokens(result.inputTokens());
    inv.setOutputTokens(result.outputTokens());
}

try (var inv = handler.invokeRemoteAgent(
        "anthropic", "claude-sonnet-4-6", "code-agent", "agent.example.com", 443)) {
    inv.setInputMessages(messages);
}
```

### Workflow 编排

```java
try (var wf = handler.workflow("data-analysis")) {
    wf.setInputMessages(List.of(
        new InputMessage("user", List.of(new TextPart("分析销售数据")))
    ));
    try (var step1 = handler.inference("openai", "gpt-4o")) { /* ... */ }
    try (var step2 = handler.tool("query_db")) { /* ... */ }
    wf.setOutputMessages(List.of(
        new OutputMessage("assistant", List.of(new TextPart("报告...")), "stop")
    ));
}
```

### 错误处理

```java
handler.inferenceRun("openai", "gpt-4o", inv -> {
    inv.setInputMessages(messages);
});

var inv = handler.inference("openai", "gpt-4o");
try {
    inv.stop();
} catch (Exception e) {
    inv.fail(e);
    throw e;
}

inv.fail("RateLimitError", "429 Too Many Requests");
```

> Java 的 `try-with-resources` 不会在异常时自动 `fail()`（与 Python `with` 不同）。异常路径请使用 `*Run()` 或显式 `fail()`。

### 流式响应

```java
import com.alibaba.loongsuite.otel.util.genai.stream.GenAiStreamWrapper;

public class MyStreamWrapper extends GenAiStreamWrapper<Chunk> {
    private final InferenceInvocation invocation;
    private final StringBuilder content = new StringBuilder();

    public MyStreamWrapper(Iterator<Chunk> delegate, InferenceInvocation invocation) {
        super(delegate);
        this.invocation = invocation;
    }

    @Override protected void processChunk(Chunk chunk) {
        content.append(chunk.text());
    }

    @Override protected void onStreamEnd() {
        invocation.setOutputMessages(List.of(
            new OutputMessage("assistant", List.of(new TextPart(content.toString())), "stop")
        ));
        invocation.stop();
    }

    @Override protected void onStreamError(Throwable error) {
        invocation.fail(error);
    }
}
```

### 自定义属性

```java
try (var inv = handler.inference("openai", "gpt-4o")) {
    inv.setAttribute("custom.request_id", "req-abc123");
    inv.setMetricAttribute("deployment.env", "production");
}
```

## 环境变量

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `OTEL_SEMCONV_STABILITY_OPT_IN` | 空 | 设为 `gen_ai_latest_experimental` 启用实验语义 |
| `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT` | 空 | `true` / `span_only` / `event_only` / `span_and_event` |
| `OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT` | 空 | `true` / `false` |
| `OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK` | 空 | `CompletionHook` 实现类全限定名 |

| 值 | Span | Event | 场景 |
|---|---|---|---|
| 未设置 | - | - | 生产默认 |
| `true` | Y | - | 向后兼容 |
| `span_only` | Y | - | 仅 span |
| `event_only` | - | Y | 推荐 |
| `span_and_event` | Y | Y | 调试 |

## CompletionHook

```java
var handler = GenAiTelemetryHandler.builder(openTelemetry)
    .setCompletionHook(ctx -> {
        var inputs = ctx.getInputs();
        var outputs = ctx.getOutputs();
    })
    .build();
```

SPI 注册：

1. 实现 `CompletionHook` 接口
2. 在 `META-INF/services/com.alibaba.loongsuite.otel.util.genai.CompletionHook` 注册
3. `export OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK=com.example.MyHook`

Hook 内异常由 `SafeCompletionHook` 捕获，不会传播到应用。

## 可运行示例

见 [examples/README.zh-CN.md](../examples/README.zh-CN.md)，覆盖 7 种 GenAI 操作。

```bash
mvn install -DskipTests
export GENAI_API_KEY=sk-xxx
cd examples && mvn spring-boot:run
```
