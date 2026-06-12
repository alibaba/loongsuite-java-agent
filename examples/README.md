# GenAI Utils Example — Spring Boot REST API

A Spring Boot application demonstrating `otel-util-genai` operation types with a real LLM provider.

Covers 7 GenAI semantic convention span types defined in OTel v1.41.1.

## Prerequisites

- Java 17+
- Maven 3.8+
- An API key for an OpenAI-compatible service (OpenAI, DashScope, Azure OpenAI, etc.)

## Quick Start

```bash
# 1. Build the parent project (from repo root)
mvn install -DskipTests

# 2. Run (pass API key via -D flag)
cd examples
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dgenai.api-key=sk-xxx"

# Or with DashScope
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="\
-Dgenai.api-key=sk-xxx \
-Dgenai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1 \
-Dgenai.provider=dashscope \
-Dgenai.model=qwen-plus"

# Or via environment variables
export GENAI_API_KEY=sk-xxx
mvn spring-boot:run
```

## API Endpoints

### POST /api/chat

LLM inference call (operation: `chat`). Produces span `chat {model}`.

**Request:**
```json
{"message": "What is OpenTelemetry?"}
```

**Response:**
```json
{
  "content": "OpenTelemetry is an open-source observability framework...",
  "model": "qwen-plus",
  "id": "chatcmpl-xxx",
  "inputTokens": 12,
  "outputTokens": 45
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is OpenTelemetry?"}'
```

---

### POST /api/embedding

Text embedding (operation: `embeddings`). Produces span `embeddings {model}`.

**Request:**
```json
{"input": "Hello world", "model": "text-embedding-v3"}
```

**Response:**
```json
{
  "model": "text-embedding-v3",
  "dimensions": 1024,
  "inputTokens": 3,
  "vectorCount": 1
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/embedding \
  -H "Content-Type: application/json" \
  -d '{"input": "Hello world"}'
```

---

### POST /api/tool

Tool execution (operation: `execute_tool`). Produces span `execute_tool {name}`.

Built-in mock tools: `get_weather`, `get_time`.

**Request:**
```json
{"name": "get_weather", "arguments": "{\"city\": \"Beijing\"}"}
```

**Response:**
```json
{
  "tool": "get_weather",
  "arguments": "{\"city\": \"Beijing\"}",
  "result": "{\"city\":\"Beijing\",\"temp\":\"28°C\",\"condition\":\"Sunny\"}"
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/tool \
  -H "Content-Type: application/json" \
  -d '{"name": "get_weather", "arguments": "{\"city\": \"Beijing\"}"}'

curl -X POST http://localhost:8080/api/tool \
  -H "Content-Type: application/json" \
  -d '{"name": "get_time"}'
```

---

### POST /api/agent

Agent invocation (operation: `invoke_agent`). Produces span `invoke_agent {name}` (SpanKind: INTERNAL).

Internally calls `/api/chat` — generates nested spans.

**Request:**
```json
{"name": "research-agent", "task": "Explain quantum computing"}
```

**Response:**
```json
{
  "agentName": "research-agent",
  "content": "Quantum computing leverages quantum mechanical phenomena...",
  "inputTokens": 18,
  "outputTokens": 120
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/agent \
  -H "Content-Type: application/json" \
  -d '{"name": "research-agent", "task": "Explain quantum computing"}'
```

---

### POST /api/workflow

Workflow orchestration (operation: `invoke_workflow`). Produces span `invoke_workflow {name}` with nested child spans (chat + tool + chat).

**Request:**
```json
{"name": "analysis-pipeline", "input": "Summarize today's weather"}
```

**Response:**
```json
{
  "workflow": "analysis-pipeline",
  "finalAnswer": "Based on the analysis...",
  "steps": [
    {"step": "analyze", "output": "The user wants..."},
    {"step": "tool:get_time", "output": "{\"time\":\"2026-06-04T10:30:00\"}"},
    {"step": "synthesize", "output": "Based on the analysis..."}
  ]
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/workflow \
  -H "Content-Type: application/json" \
  -d '{"name": "analysis-pipeline", "input": "Summarize today"}'
```

---

### POST /api/retrieval

Document retrieval / RAG search (operation: `retrieval`). Produces span `retrieval {dataSourceId}`.

Returns mock search results (no external vector DB required).

**Request:**
```json
{"query": "What is OpenTelemetry?", "dataSourceId": "knowledge-base", "topK": 5}
```

**Response:**
```json
{
  "dataSourceId": "knowledge-base",
  "query": "What is OpenTelemetry?",
  "documents": [
    {"id": "doc-001", "score": 0.95, "snippet": "OpenTelemetry is an observability framework..."},
    {"id": "doc-002", "score": 0.87, "snippet": "Semantic conventions define attribute names..."},
    {"id": "doc-003", "score": 0.72, "snippet": "GenAI spans track LLM operations..."}
  ]
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/retrieval \
  -H "Content-Type: application/json" \
  -d '{"query": "What is OpenTelemetry?", "dataSourceId": "kb-01", "topK": 3}'
```

---

### POST /api/create-agent

Agent creation (operation: `create_agent`). Produces span `create_agent {name}` (SpanKind: CLIENT).

**Request:**
```json
{
  "name": "research-bot",
  "description": "Research assistant for scientific papers",
  "instructions": "You are a research assistant. Always cite sources."
}
```

**Response:**
```json
{
  "agentId": "agent-a1b2c3d4",
  "name": "research-bot",
  "description": "Research assistant for scientific papers",
  "model": "qwen-plus"
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/create-agent \
  -H "Content-Type: application/json" \
  -d '{"name": "research-bot", "description": "Research assistant", "instructions": "Always cite sources."}'
```

---

## Configuration

All settings can be provided via `-D` flags, environment variables, or `application.yml`:

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `genai.api-key` | `GENAI_API_KEY` | _(required)_ | LLM API key |
| `genai.base-url` | `GENAI_BASE_URL` | `https://api.openai.com/v1` | API endpoint |
| `genai.provider` | `GENAI_PROVIDER` | `openai` | Provider name in telemetry |
| `genai.model` | `GENAI_MODEL` | `gpt-4o` | Model name |
| `genai.temperature` | `GENAI_TEMPERATURE` | `0.7` | Sampling temperature |
| `genai.max-tokens` | `GENAI_MAX_TOKENS` | `1024` | Max output tokens |

### OpenTelemetry Settings

| Property | Default | Description |
|----------|---------|-------------|
| `otel.service.name` | `genai-example` | Service name in traces |
| `otel.traces.exporter` | `logging` | `logging` / `otlp` / `none` |
| `otel.metrics.exporter` | `logging` | `logging` / `otlp` / `none` |
| `otel.logs.exporter` | `none` | `logging` / `otlp` / `none` |

## Semantic Convention Validation (Weaver Live Check)

This example uses **OTel SDK autoconfigure only** (no `opentelemetry-spring-boot-starter`),
so Weaver receives mostly GenAI spans/metrics/events — without JVM, HTTP server, or SDK-internal noise.

See [docs/weaver-live-check.md](../docs/weaver-live-check.md) for the full procedure.

Steps:

```bash
# Terminal 1: Weaver
weaver registry live-check \
  --registry "https://github.com/open-telemetry/semantic-conventions/archive/refs/tags/v1.41.1.tar.gz[model]" \
  --otlp-grpc-port 4317 --admin-port 4320 --inactivity-timeout 120 --output http

# Terminal 2: Example (OTLP → Weaver)
cd examples
mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
-Dgenai.api-key=sk-xxx \
-Dotel.traces.exporter=otlp \
-Dotel.metrics.exporter=otlp \
-Dotel.logs.exporter=otlp \
-Dotel.exporter.otlp.endpoint=http://localhost:4317 \
-Dotel.exporter.otlp.protocol=grpc"

# Terminal 3: Trigger all 7 span types, wait for metrics flush, then stop
# (see weaver-live-check.md for curl commands)
sleep 6 && curl -X POST http://localhost:4320/stop
```

## Running with Javaagent (Full Trace Coverage)

The example does not auto-instrument Spring WebMVC or the OkHttp client inside the OpenAI SDK.
To get **complete end-to-end traces** (HTTP server → GenAI → HTTP client), attach the OTel Javaagent:

```bash
mvn package -DskipTests

java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.instrumentation.experimental.span-suppression-strategy=none \
  -Dotel.service.name=java-genai-utils \
  -Dotel.traces.exporter=otlp \
  -Dotel.metrics.exporter=otlp \
  -Dotel.logs.exporter=none \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.exporter.otlp.traces.endpoint=https://your-endpoint/v1/traces \
  -Dotel.exporter.otlp.metrics.endpoint=https://your-endpoint/v1/metrics \
  -Dotel.exporter.otlp.headers="x-arms-license-key=xxx" \
  -Dgenai.api-key=sk-xxx \
  -Dgenai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1 \
  -Dgenai.provider=dashscope \
  -Dgenai.model=qwen-plus \
  -jar target/examples-0.1.0-SNAPSHOT.jar
```

| Mode | HTTP Server Span | GenAI Span | HTTP Client Span |
|------|-----------------|------------|-----------------|
| SDK only (default) | ❌ | ✅ | ❌ |
| javaagent + library | ✅ | ✅ | ✅ |

## Telemetry Produced

Each API call produces corresponding OTel signals:

| Endpoint | Span Name | SpanKind | Metrics | Events (default config) |
|----------|-----------|----------|---------|-------------------------|
| `/api/chat` | `chat {model}` | CLIENT | duration + token | `gen_ai.client.inference.operation.details` |
| `/api/embedding` | `embeddings {model}` | CLIENT | duration + token | — |
| `/api/tool` | `execute_tool {name}` | INTERNAL | duration | — |
| `/api/agent` | `invoke_agent {name}` | INTERNAL | duration + token | nested chat events |
| `/api/workflow` | `invoke_workflow {name}` | INTERNAL | duration | nested chat events |
| `/api/retrieval` | `retrieval {dataSourceId}` | CLIENT | duration | — |
| `/api/create-agent` | `create_agent {name}` | CLIENT | duration | — |

Default `application.yml` enables `otel.semconv.stability.opt.in=gen_ai_latest_experimental`,
`capture.message.content=span_and_event`, and `emit.event=true`, so chat (and nested chat in
agent/workflow) also emits inference log events. Any invocation that calls `fail(Throwable)` emits
`gen_ai.client.operation.exception` (with `exception.stacktrace`).

With `logging` exporter, spans and metrics print to the console; set `otel.logs.exporter=logging`
to see log events locally.

## Project Structure

```
examples/
├── pom.xml
├── README.md
└── src/main/
    ├── java/.../example/
    │   ├── ExampleApplication.java    # Spring Boot entry point
    │   ├── GenAiConfig.java           # Beans: OpenTelemetry, Handler, OpenAIClient
    │   ├── ChatController.java        # All 7 REST endpoints
    │   ├── ChatService.java           # chat (inference)
    │   ├── EmbeddingService.java      # embedding
    │   ├── ToolService.java           # execute_tool (mock tools)
    │   ├── AgentService.java          # invoke_agent
    │   ├── WorkflowService.java       # invoke_workflow (multi-step)
    │   ├── RetrievalService.java      # retrieval (mock search)
    │   └── CreateAgentService.java    # create_agent
    └── resources/
        └── application.yml            # All configuration
```
