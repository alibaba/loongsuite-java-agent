# Usage Guide

**English** | [中文](USAGE_zh.md)

Detailed guide for `otel-util-genai`. For installation and Maven coordinates, see [README.md](../README.md).

## Quick Start

### 1. Initialize OpenTelemetry

```bash
export OTEL_SERVICE_NAME=my-genai-app
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

```java
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

var openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
```

### 2. Create a Handler and Instrument an LLM Call

```java
import com.alibaba.loongsuite.otel.util.genai.*;
import com.alibaba.loongsuite.otel.util.genai.types.*;
import java.util.List;

var handler = GenAiTelemetryHandler.create(openTelemetry);

try (var inv = handler.inference("openai", "gpt-4o")) {
    inv.setInputMessages(List.of(
        new InputMessage("user", List.of(new TextPart("Hello")))
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

## Operation Types

| Operation | Factory Method | Span Name | SpanKind |
|---|---|---|---|
| LLM inference | `handler.inference("openai", "gpt-4o")` | `chat gpt-4o` | CLIENT |
| Embedding | `handler.embedding("openai", "text-embedding-3-small")` | `embeddings text-embedding-3-small` | CLIENT |
| Tool execution | `handler.tool("get_weather")` | `execute_tool get_weather` | INTERNAL |
| Local agent | `handler.invokeLocalAgent("openai", "gpt-4o", "research")` | `invoke_agent research` | INTERNAL |
| Remote agent | `handler.invokeRemoteAgent("openai", "gpt-4o", "code", "a.co", 443)` | `invoke_agent code` | CLIENT |
| Workflow | `handler.workflow("pipeline")` | `invoke_workflow pipeline` | INTERNAL |
| Retrieval | `handler.retrieval("openai", "kb-01", "api.example.com", 443)` | `retrieval kb-01` | CLIENT |
| Create agent | `handler.createAgent("openai", "gpt-4o", "research-bot", "api.example.com", 443)` | `create_agent research-bot` | CLIENT |

## Examples by Operation

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

### Tool Execution

```java
try (var inv = handler.tool("get_weather", "call_001", "function", "Get weather")) {
    inv.setArguments("{\"city\": \"Beijing\"}");
    var result = weatherService.getWeather("Beijing");
    inv.setToolResult(result);
}
```

### Agent

```java
try (var inv = handler.invokeLocalAgent("openai", "gpt-4o", "research-agent")) {
    inv.setAgentId("agent-001");
    inv.setInputMessages(List.of(
        new InputMessage("user", List.of(new TextPart("Research quantum computing")))
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

### Workflow Orchestration

```java
try (var wf = handler.workflow("data-analysis")) {
    wf.setInputMessages(List.of(
        new InputMessage("user", List.of(new TextPart("Analyze sales data")))
    ));
    try (var step1 = handler.inference("openai", "gpt-4o")) { /* ... */ }
    try (var step2 = handler.tool("query_db")) { /* ... */ }
    wf.setOutputMessages(List.of(
        new OutputMessage("assistant", List.of(new TextPart("Report...")), "stop")
    ));
}
```

### Error Handling

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

> Java `try-with-resources` does **not** auto-call `fail()` on exceptions (unlike Python's `with`).
> Use `*Run()` callbacks or explicit `fail()` on error paths.

### Streaming Responses

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

### Custom Attributes

```java
try (var inv = handler.inference("openai", "gpt-4o")) {
    inv.setAttribute("custom.request_id", "req-abc123");
    inv.setMetricAttribute("deployment.env", "production");
}
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `OTEL_SEMCONV_STABILITY_OPT_IN` | empty | Set to `gen_ai_latest_experimental` to enable experimental semconv |
| `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT` | empty | `true` / `span_only` / `event_only` / `span_and_event` |
| `OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT` | empty | `true` / `false` |
| `OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK` | empty | Fully qualified `CompletionHook` class name |

| Value | Span | Event | Use Case |
|---|---|---|---|
| unset | - | - | Production default |
| `true` | Y | - | Backward compatible |
| `span_only` | Y | - | Span only |
| `event_only` | - | Y | Recommended |
| `span_and_event` | Y | Y | Debugging |

## CompletionHook

```java
var handler = GenAiTelemetryHandler.builder(openTelemetry)
    .setCompletionHook(ctx -> {
        var inputs = ctx.getInputs();
        var outputs = ctx.getOutputs();
    })
    .build();
```

SPI registration:

1. Implement `CompletionHook`
2. Register in `META-INF/services/com.alibaba.loongsuite.otel.util.genai.CompletionHook`
3. `export OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK=com.example.MyHook`

Exceptions inside hooks are caught by `SafeCompletionHook` and do not propagate.

## Runnable Example

See [examples/README.md](../examples/README.md) for the Spring Boot demo covering 7 GenAI operations.

```bash
mvn install -DskipTests
export GENAI_API_KEY=sk-xxx
cd examples && mvn spring-boot:run
```
