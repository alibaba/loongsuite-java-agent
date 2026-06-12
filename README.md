# LoongSuite Java GenAI Utils

**English** | [中文](README_zh.md)

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

```xml
<groupId>com.alibaba.loongsuite</groupId>
<artifactId>otel-util-genai</artifactId>
<version>0.1.0-SNAPSHOT</version>
```

## Installation

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>1.62.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <dependency>
        <groupId>com.alibaba.loongsuite</groupId>
        <artifactId>otel-util-genai</artifactId>
        <version>0.1.0-SNAPSHOT</version>
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
implementation platform('io.opentelemetry:opentelemetry-bom:1.62.0')
implementation 'com.alibaba.loongsuite:otel-util-genai:0.1.0-SNAPSHOT'
implementation 'io.opentelemetry:opentelemetry-sdk'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

> This library depends only on the OTel API. Without an SDK and exporter, operations are no-ops.

## Quick Start

```java
var openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
var handler = GenAiTelemetryHandler.create(openTelemetry);

try (var inv = handler.inference("openai", "gpt-4o")) {
    inv.setInputMessages(List.of(new InputMessage("user", List.of(new TextPart("Hello")))));
    var response = client.chat(request);
    inv.setOutputMessages(List.of(
        new OutputMessage("assistant", List.of(new TextPart(response.content())), "stop")));
}
```

See [docs/USAGE.md](docs/USAGE.md) for all operation types, streaming, error handling, environment variables, and CompletionHook.

## Documentation

| Document | Description |
|----------|-------------|
| [docs/USAGE.md](docs/USAGE.md) | Usage guide (operations, config, hooks) |
| [examples/README.md](examples/README.md) | Spring Boot example (7 GenAI operations) |

## Requirements

- Java 17+
- OpenTelemetry API 1.62.0+

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

Apache License 2.0
