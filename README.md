# LoongSuite Java GenAI Utils

## Introduction

LoongSuite Java GenAI Utils is a key component of LoongSuite, Alibaba's unified observability data collection suite, providing shared telemetry lifecycle management for Java GenAI instrumentation.

LoongSuite includes the following key components:

* [LoongCollector](https://github.com/alibaba/loongcollector): universal node agent for log, metric, and network collection based on eBPF.
* [LoongSuite Java](https://github.com/alibaba/loongsuite-java): GenAI telemetry utility library for Java.
* [LoongSuite Python](https://github.com/alibaba/loongsuite-python): process agent for Python applications.
* [LoongSuite Go](https://github.com/alibaba/loongsuite-go): compile-time instrumentation agent for Golang.
* [LoongSuite JS](https://github.com/alibaba/loongsuite-js): OpenTelemetry plugins for JavaScript AI agents.

Built on [OTel GenAI Semantic Conventions v1.41.1](https://github.com/open-telemetry/semantic-conventions/tree/v1.41.1/docs/gen-ai), this library is the Java counterpart of [opentelemetry-util-genai](https://github.com/open-telemetry/opentelemetry-python-genai/tree/main/util/opentelemetry-util-genai).

## Maven Coordinates

Replace `${otel-util-genai.version}` with the [latest `otel-util-genai` release](https://central.sonatype.com/artifact/com.alibaba.loongsuite/otel-util-genai) from Maven Central.

```xml
<dependency>
    <groupId>com.alibaba.loongsuite</groupId>
    <artifactId>otel-util-genai</artifactId>
    <version>${otel-util-genai.version}</version>
</dependency>
```

## Installation

Replace `${otel-util-genai.version}` and `${opentelemetry.version}` with the latest releases of [`otel-util-genai`](https://central.sonatype.com/artifact/com.alibaba.loongsuite/otel-util-genai) and [`opentelemetry-bom`](https://central.sonatype.com/artifact/io.opentelemetry/opentelemetry-bom) from Maven Central.

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>${opentelemetry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <dependency>
        <groupId>com.alibaba.loongsuite</groupId>
        <artifactId>otel-util-genai</artifactId>
        <version>${otel-util-genai.version}</version>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk</artifactId>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>
</dependencies>
```

### Gradle

```groovy
// Use the latest releases from Maven Central
def otelUtilGenaiVersion = '...'      // otel-util-genai
def opentelemetryBomVersion = '...'   // opentelemetry-bom

implementation platform("io.opentelemetry:opentelemetry-bom:${opentelemetryBomVersion}")
implementation "com.alibaba.loongsuite:otel-util-genai:${otelUtilGenaiVersion}"
implementation 'io.opentelemetry:opentelemetry-sdk'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

> This library depends only on the OTel API. Without an SDK and exporter, operations are no-ops.

## Quick Start

```java
OpenTelemetry openTelemetry =
    AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
GenAiTelemetryHandler handler = GenAiTelemetryHandler.create(openTelemetry);

try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
    inv.setInputMessages(
        Collections.singletonList(
            new InputMessage("user", Collections.singletonList(new TextPart("Hello")))));
    // Call your LLM client here, then record the response:
    inv.setOutputMessages(
        Collections.singletonList(
            new OutputMessage(
                "assistant",
                Collections.singletonList(new TextPart("Hi there!")),
                "stop")));
}
```

See [docs/USAGE.md](docs/USAGE.md) for all operation types, streaming, error handling, environment variables, and CompletionHook.

## Documentation

| Document | Description |
|----------|-------------|
| [docs/USAGE.md](docs/USAGE.md) | Usage guide (operations, config, hooks) |
| [examples/README.md](examples/README.md) | Spring Boot example (7 GenAI operations) |

## Requirements

- Java 8+
- OpenTelemetry API (use the latest `opentelemetry-bom` release)

## Community

We are looking forward to your feedback and suggestions. You can join
our [DingTalk user group](https://qr.dingtalk.com/action/joingroup?code=v1,k1,VaFSqbGiRY0iAL3GGd18DRWDyb1HpgOuyfDzsX3Drng=&_dt_no_comment=1&origin=11?) and [DingTalk developer group](https://qr.dingtalk.com/action/joingroup?code=v1,k1,p++Tn/fCshqbhFYK69wXUPUyQ6+W15jxiyfiicfdNPw=&_dt_no_comment=1&origin=11?)
to engage with us.

| User Group | Developer Group |
|------------|-----------------|
| <img src="docs/_assets/img/dingtalk-chat-group.jpg" height="150"> | <img src="docs/_assets/img/dev-group.jpg" height="150"> |

## Resources

* AgentScope: https://github.com/modelscope/agentscope
* Observability Community: https://observability.cn

## License

This project is licensed under the [Apache License 2.0](LICENSE)
