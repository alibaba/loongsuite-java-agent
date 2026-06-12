# OTel Weaver Live Check

## What Is It

[OTel Weaver](https://github.com/open-telemetry/weaver) `live-check` validates **actual telemetry data** against a semantic convention registry at runtime. It catches issues that static analysis cannot: wrong attribute values, missing required attributes, incorrect span naming, invalid status codes, etc.

## How It Works

```
┌─────────────────┐       OTLP gRPC        ┌──────────────────┐
│  Your App        │ ──────────────────────► │  Weaver          │
│  (OTel SDK +    │       port 4317         │  live-check      │
│   OTLP Exporter)│                         │                  │
└─────────────────┘                         │  Loads semconv   │
                                            │  registry YAML   │
        POST /stop                          │  + Rego policies │
       ◄────────────────────────────────────│                  │
       (JSON report with violations)        └──────────────────┘
```

1. Weaver starts a gRPC OTLP receiver
2. Your app sends spans/metrics/logs via standard OTLP exporter to Weaver
3. Weaver validates each signal against the semconv registry using built-in Rego policies
4. You POST `/stop` and get a violation report

> The `examples/` app uses OTel SDK autoconfigure only (no `opentelemetry-spring-boot-starter`),
> so the report focuses on GenAI signals instead of JVM/HTTP/SDK-internal noise.

## Installing Weaver

```bash
# macOS (Apple Silicon)
curl -sL "https://github.com/open-telemetry/weaver/releases/download/v0.23.0/weaver-aarch64-apple-darwin.tar.xz" -o /tmp/weaver.tar.xz
tar xf /tmp/weaver.tar.xz -C /tmp
mkdir -p ~/tools && cp /tmp/weaver-aarch64-apple-darwin/weaver ~/tools/weaver && chmod +x ~/tools/weaver

# macOS (Intel) — replace aarch64 with x86_64

# Linux (amd64)
curl -sL "https://github.com/open-telemetry/weaver/releases/download/v0.23.0/weaver-x86_64-unknown-linux-gnu.tar.xz" -o /tmp/weaver.tar.xz
tar xf /tmp/weaver.tar.xz -C /tmp
sudo cp /tmp/weaver-x86_64-unknown-linux-gnu/weaver /usr/local/bin/

# Verify
weaver --version  # weaver 0.23.0
```

## Validating with the Example App

### Step 1: Start Weaver

```bash
weaver registry live-check \
  --registry "https://github.com/open-telemetry/semantic-conventions/archive/refs/tags/v1.41.1.tar.gz[model]" \
  --otlp-grpc-port 4317 \
  --admin-port 4320 \
  --inactivity-timeout 120 \
  --output http
```

### Step 2: Start the example app with OTLP exporter pointing at Weaver

```bash
cd examples
mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
-Dgenai.api-key=sk-xxx \
-Dgenai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1 \
-Dgenai.provider=dashscope \
-Dgenai.model=qwen-plus \
-Dotel.traces.exporter=otlp \
-Dotel.metrics.exporter=otlp \
-Dotel.logs.exporter=otlp \
-Dotel.exporter.otlp.endpoint=http://localhost:4317 \
-Dotel.exporter.otlp.protocol=grpc"
```

### Step 3: Trigger all 7 span types

```bash
# Chat (inference)
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" -d '{"message":"Hello"}'

# Embedding
curl -s -X POST http://localhost:8080/api/embedding \
  -H "Content-Type: application/json" -d '{"input":"Hello world"}'

# Tool
curl -s -X POST http://localhost:8080/api/tool \
  -H "Content-Type: application/json" -d '{"name":"get_time"}'

# Retrieval
curl -s -X POST http://localhost:8080/api/retrieval \
  -H "Content-Type: application/json" -d '{"query":"test","dataSourceId":"kb-01","topK":3}'

# Create agent
curl -s -X POST http://localhost:8080/api/create-agent \
  -H "Content-Type: application/json" -d '{"name":"bot","description":"test","instructions":"help"}'

# Agent
curl -s -X POST http://localhost:8080/api/agent \
  -H "Content-Type: application/json" -d '{"name":"test-agent","task":"Hello"}'

# Workflow
curl -s -X POST http://localhost:8080/api/workflow \
  -H "Content-Type: application/json" -d '{"name":"test-flow","input":"summarize"}'
```

### Step 4: Wait for metrics export, then get the report

Metrics use a 5 s export interval by default (`otel.metric.export.interval` in `application.yml`).
Wait before stopping Weaver so `gen_ai.client.*` metrics are included.

```bash
sleep 6
curl -X POST http://localhost:4320/stop
```

### Interpreting the report

Focus on **GenAI-related violations** (`gen_ai.*` spans, metrics, and log events).
`improvement`-level advisories about `development` stability are expected and can be ignored.

A clean GenAI run should have no `violation` entries on:

- Span names like `chat {model}`, `embeddings {model}`, `execute_tool {name}`, etc.
- `gen_ai.client.operation.duration` and `gen_ai.client.token.usage` metrics
- Log events when content capture is enabled (`application.yml` defaults to `span_and_event`)

## Built-in Static Validation

In addition to Weaver Live Check, the library includes `SemconvComplianceTest` which runs automatically in every `mvn test`:

- Scans all Java source files for `gen_ai.*` attribute strings
- Cross-checks each one against `registry.yaml` (v1.41.1)
- Verifies required attributes are present for each span type
- No external tools needed

```bash
mvn test -pl otel-util-genai
# SemconvComplianceTest: 9 tests, 0 failures
```

## Rego Validation Rules

Weaver's built-in policies check:

| Rule ID | What It Checks |
|---------|---------------|
| `missing_attribute` | Attribute key not in the registry |
| `genai_operation_name_unknown` | `gen_ai.operation.name` is not a valid enum value |
| `genai_span_status_ok_set_by_instrumentation` | Instrumentation must NOT set `status.code=OK` on success |
| `genai_content_schema` | Message JSON doesn't match the GenAI message schema |
| `genai_expected_attribute_missing` | Required attribute missing for the span type |
| `genai_span_name_format` | Span name doesn't follow `{operation} {qualifier}` format |

## CLI Reference

### Registry Sources

```bash
# Specific version
--registry "https://github.com/open-telemetry/semantic-conventions/archive/refs/tags/v1.41.1.tar.gz[model]"

# Latest main
--registry "https://github.com/open-telemetry/semantic-conventions.git[model]"

# Local
--registry "/path/to/semantic-conventions/model"
```

### Key Options

| Option | Default | Description |
|--------|---------|-------------|
| `--registry <URL>` | OTel upstream | Semconv registry source |
| `--otlp-grpc-port <PORT>` | 4317 | OTLP gRPC receiver port |
| `--admin-port <PORT>` | 4320 | Admin HTTP API port |
| `--inactivity-timeout <SEC>` | 10 | Auto-stop after N seconds of no data |
| `--output <PATH>` | stdout | `"none"` or `"http"` (return via /stop) |
| `--format <FMT>` | ansi | `json` / `yaml` / `jsonl` / `ansi` |
| `--skip-policies` | false | Skip all policy checks |
| `--future` | false | Enable latest rules |

## References

- [OTel Weaver](https://github.com/open-telemetry/weaver)
- [OTel GenAI Semconv v1.41.1](https://github.com/open-telemetry/semantic-conventions/tree/v1.41.1/docs/gen-ai)
- [GenAI conformance issue #86](https://github.com/open-telemetry/opentelemetry-python-genai/issues/86)
