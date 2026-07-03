# LoongSuite Java GenAI Utils

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

将 `${otel-util-genai.version}` 替换为 Maven Central 上 [`otel-util-genai` 最新版本](https://central.sonatype.com/artifact/com.alibaba.loongsuite/otel-util-genai)。

```xml
<dependency>
    <groupId>com.alibaba.loongsuite</groupId>
    <artifactId>otel-util-genai</artifactId>
    <version>${otel-util-genai.version}</version>
</dependency>
```

## 安装

将 `${otel-util-genai.version}` 与 `${opentelemetry.version}` 分别替换为 Maven Central 上 [`otel-util-genai`](https://central.sonatype.com/artifact/com.alibaba.loongsuite/otel-util-genai) 与 [`opentelemetry-bom`](https://central.sonatype.com/artifact/io.opentelemetry/opentelemetry-bom) 的最新发布版本。

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
// 使用 Maven Central 上的最新版本
def otelUtilGenaiVersion = '...'      // otel-util-genai
def opentelemetryBomVersion = '...'   // opentelemetry-bom

implementation platform("io.opentelemetry:opentelemetry-bom:${opentelemetryBomVersion}")
implementation "com.alibaba.loongsuite:otel-util-genai:${otelUtilGenaiVersion}"
implementation 'io.opentelemetry:opentelemetry-sdk'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

> 本库仅依赖 OTel API；未配置 SDK 与 Exporter 时不会产出遥测数据。

## 快速开始

```java
OpenTelemetry openTelemetry =
    AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
GenAiTelemetryHandler handler = GenAiTelemetryHandler.create(openTelemetry);

try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
    inv.setInputMessages(
        Collections.singletonList(
            new InputMessage("user", Collections.singletonList(new TextPart("你好")))));
    // 调用你的 LLM 客户端后，记录响应：
    inv.setOutputMessages(
        Collections.singletonList(
            new OutputMessage(
                "assistant",
                Collections.singletonList(new TextPart("你好！")),
                "stop")));
}
```

完整用法（操作类型、流式、错误处理、环境变量、CompletionHook）见 [docs/USAGE_zh.md](docs/USAGE_zh.md)。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/USAGE_zh.md](docs/USAGE_zh.md) | 使用指南 |
| [examples/README.zh-CN.md](examples/README.zh-CN.md) | Spring Boot 示例（7 种 GenAI 操作） |

## 要求

- Java 8+
- OpenTelemetry API（使用最新的 `opentelemetry-bom` 发布版本）

## 社区

欢迎反馈与建议。可加入 [钉钉用户群](https://qr.dingtalk.com/action/joingroup?code=v1,k1,VaFSqbGiRY0iAL3GGd18DRWDyb1HpgOuyfDzsX3Drng=&_dt_no_comment=1&origin=11?) 与 [钉钉开发者群](https://qr.dingtalk.com/action/joingroup?code=v1,k1,p++Tn/fCshqbhFYK69wXUPUyQ6+W15jxiyfiicfdNPw=&_dt_no_comment=1&origin=11?)。

| 用户群 | 开发者群 |
|--------|----------|
| <img src="docs/_assets/img/dingtalk-chat-group.jpg" height="150"> | <img src="docs/_assets/img/dev-group.jpg" height="150"> |

## 相关资源

* AgentScope：https://github.com/modelscope/agentscope
* 可观测性社区：https://observability.cn

## License

This project is licensed under the [Apache License 2.0](LICENSE)
