# LoongSuite Java GenAI Utils

[English](README.md) | **中文**

## 简介

LoongSuite Java GenAI Utils 是阿里巴巴统一可观测数据采集套件 LoongSuite 的关键组件，为 Java GenAI instrumentation 提供共享的遥测生命周期管理能力。

LoongSuite 包含以下关键组件：

* [LoongCollector](https://github.com/alibaba/loongcollector)：基于 eBPF 的通用节点 Agent。
* [LoongSuite Java](https://github.com/alibaba/loongsuite-java)：Java GenAI 遥测工具库。
* [LoongSuite Python](https://github.com/alibaba/loongsuite-python)：Python 应用进程级 Agent。
* [LoongSuite Go](https://github.com/alibaba/loongsuite-go)：Golang 编译期 instrumentation Agent。
* [LoongSuite JS](https://github.com/alibaba/loongsuite-js)：JavaScript AI Agent 的 OpenTelemetry 插件。

基于 [OTel GenAI Semantic Conventions v1.41.1](https://github.com/open-telemetry/semantic-conventions/tree/v1.41.1/docs/gen-ai) 实现，是 [opentelemetry-util-genai](https://github.com/open-telemetry/opentelemetry-python-genai/tree/main/util/opentelemetry-util-genai) 的 Java 对等实现。

## Maven 坐标

```xml
<groupId>com.alibaba.loongsuite</groupId>
<artifactId>otel-util-genai</artifactId>
<version>0.1.0-SNAPSHOT</version>
```

## 安装

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

> 本库仅依赖 OTel API；未配置 SDK 与 Exporter 时不会产出遥测数据。

## 快速开始

```java
var openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
var handler = GenAiTelemetryHandler.create(openTelemetry);

try (var inv = handler.inference("openai", "gpt-4o")) {
    inv.setInputMessages(List.of(new InputMessage("user", List.of(new TextPart("你好")))));
    var response = client.chat(request);
    inv.setOutputMessages(List.of(
        new OutputMessage("assistant", List.of(new TextPart(response.content())), "stop")));
}
```

完整用法（操作类型、流式、错误处理、环境变量、CompletionHook）见 [docs/USAGE_zh.md](docs/USAGE_zh.md)。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/USAGE_zh.md](docs/USAGE_zh.md) | 使用指南 |
| [examples/README.md](examples/README.md) | Spring Boot 示例（7 种 GenAI 操作） |

## 要求

- Java 17+
- OpenTelemetry API 1.62.0+

## 社区

欢迎反馈与建议。可加入 [钉钉用户群](https://qr.dingtalk.com/action/joingroup?code=v1,k1,VaFSqbGiRY0iAL3GGd18DRWDyb1HpgOuyfDzsX3Drng=&_dt_no_comment=1&origin=11?) 与 [钉钉开发者群](https://qr.dingtalk.com/action/joingroup?code=v1,k1,p++Tn/fCshqbhFYK69wXUPUyQ6+W15jxiyfiicfdNPw=&_dt_no_comment=1&origin=11?)。

| 用户群 | 开发者群 |
|--------|----------|
| <img src="docs/_assets/img/dingtalk-chat-group.jpg" height="150"> | <img src="docs/_assets/img/dev-group.jpg" height="150"> |

## 相关资源

* AgentScope：https://github.com/modelscope/agentscope
* 可观测性社区：https://observability.cn

## License

Apache License 2.0
