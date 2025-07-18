# LongSuite Java Agent

[![Release](https://img.shields.io/github/v/release/alibaba/loongsuite-java-agent?include_prereleases&style=)](https://github.com/alibaba/loongsuite-java-agent/releases/)

A Java agent based on [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) with Alibaba extensions and AI-related instrumentations.

* [About](#about)
* [Alibaba Extensions and AI Instrumentations](#alibaba-extensions-and-ai-instrumentations)
* [Getting Started](#getting-started)
* [Configuring the Agent](#configuring-the-agent)
* [Supported libraries, frameworks, and application servers](#supported-libraries-frameworks-and-application-servers)
* [Creating agent extensions](#creating-agent-extensions)
* [Manually instrumenting](#manually-instrumenting)
* [Logger MDC auto-instrumentation](#logger-mdc-mapped-diagnostic-context-auto-instrumentation)
* [Troubleshooting](#troubleshooting)
* [Contributing](#contributing)

## About

This project is based on [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) and provides a Java agent JAR that can be attached to any Java 8+ application and dynamically injects bytecode to capture telemetry from a number of popular libraries and frameworks.

In addition to the comprehensive instrumentation provided by OpenTelemetry, this distribution includes:
- **Alibaba Extensions**: Custom instrumentations and integrations for Alibaba ecosystem tools
- **AI-related Instrumentations**: Enhanced support for AI and machine learning frameworks with specialized telemetry collection

You can export the telemetry data in a variety of formats.
You can also configure the agent and exporter via command line arguments
or environment variables. The net result is the ability to gather telemetry
data from a Java application without code changes.

This repository also publishes standalone instrumentation for several libraries (and growing)
that can be used if you prefer that over using the Java agent.
Please see the standalone library instrumentation column
on [Supported Libraries](docs/supported-libraries.md#libraries--frameworks).
If you are looking for documentation on using those.

## Alibaba Extensions and AI Instrumentations

This distribution extends the base OpenTelemetry Java instrumentation with additional capabilities:

### Alibaba Ecosystem Support
- **Alibaba Druid**: Enhanced connection pool monitoring and database performance tracking
- Additional Alibaba cloud services instrumentations (more coming soon)

### AI and Machine Learning Instrumentations
- **GenAI Support**: Specialized instrumentation for generative AI applications with token usage tracking, model performance metrics, and prompt/response logging
- Enhanced observability for AI/ML workloads

### Extension Examples

The [examples/distro](examples/distro) directory demonstrates how to extend the OpenTelemetry Java agent with custom functionality. Available examples include:

- **[DemoIdGenerator](examples/distro/custom/src/main/java/com/example/javaagent/DemoIdGenerator.java)** - Custom trace and span ID generation
- **[DemoPropagator](examples/distro/custom/src/main/java/com/example/javaagent/DemoPropagator.java)** - Custom context propagation across service boundaries  
- **[DemoSampler](examples/distro/custom/src/main/java/com/example/javaagent/DemoSampler.java)** - Custom sampling strategies for trace collection
- **[DemoSpanProcessor](examples/distro/custom/src/main/java/com/example/javaagent/DemoSpanProcessor.java)** - Custom span processing and enrichment
- **[DemoSpanExporter](examples/distro/custom/src/main/java/com/example/javaagent/DemoSpanExporter.java)** - Custom telemetry export destinations

These examples serve as templates for creating your own agent distribution with custom extensions. See the [Distribution README](examples/distro/README.md) for detailed guidance on extending functionality.

## Getting Started

Download
the [latest version](https://github.com/alibaba/loongsuite-java-agent/releases/latest/download/opentelemetry-javaagent.jar).

This package includes the instrumentation agent as well as
instrumentations for all supported libraries and all available data exporters.
The package provides a completely automatic, out-of-the-box experience.

Enable the instrumentation agent using the `-javaagent` flag to the JVM.

```
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -jar myapp.jar
```

By default, the OpenTelemetry Java agent uses the
[OTLP exporter](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp)
configured to send data to an
[OpenTelemetry collector](https://github.com/open-telemetry/opentelemetry-collector/blob/main/receiver/otlpreceiver/README.md)
at `http://localhost:4318`.

Configuration parameters are passed as Java system properties (`-D` flags) or
as environment variables. See [the configuration documentation][config-agent]
for the full list of configuration items. For example:

```
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.resource.attributes=service.name=your-service-name \
     -Dotel.traces.exporter=zipkin \
     -jar myapp.jar
```

## Configuring the Agent

The agent is highly configurable! Many aspects of the agent's behavior can be
configured for your needs, such as exporter choice, exporter config (like where
data is sent), trace context propagation headers, and much more.

For a detailed list of agent configuration options, see the [agent configuration docs][config-agent].

For a detailed list of additional SDK configuration environment variables and system properties,
see the [SDK configuration docs][config-sdk].

*Note: Config parameter names are very likely to change over time, so please check
back here when trying out a new version!
Please [report any bugs](https://github.com/alibaba/loongsuite-java-agent/issues)
or unexpected behavior you find.*

## Supported libraries, frameworks, and application servers

We support an impressively huge number
of [libraries and frameworks](docs/supported-libraries.md#libraries--frameworks) and
a majority of the most
popular [application servers](docs/supported-libraries.md#application-servers)...right out of the
box!
[Click here to see the full list](docs/supported-libraries.md) and to learn more about
[disabled instrumentation](docs/supported-libraries.md#disabled-instrumentations)
and how to [suppress unwanted instrumentation][suppress].

## Creating agent extensions

[Extensions](examples/extension/README.md) add new features and capabilities to the agent without
having to create a separate distribution or to fork this repository. For example, you can create
custom samplers or span exporters, set new defaults, and embed it all in the agent to obtain a
single jar file.

## Creating an agent distribution

The [examples/distro](examples/distro/README.md) directory provides comprehensive guidance and examples for creating a separate distribution, serving as a collection of practical examples for extending the functionality of the OpenTelemetry Java instrumentation agent. It demonstrates how to repackage the agent while incorporating custom features such as:

- Custom sampling strategies
- Custom span processors and exporters  
- Custom context propagators
- Additional instrumentation modules
- Resource providers and auto-configuration customizers

The distro examples show how to build your own agent distribution with Alibaba-specific or domain-specific customizations. [Agent extensions](#creating-agent-extensions) are recommended instead for most users as they are simpler and do not require rebuilding with each OpenTelemetry Java agent release.

## Manually instrumenting

For most users, the out-of-the-box instrumentation is completely sufficient and nothing more has to
be done. Sometimes, however, users wish to add attributes to the otherwise automatic spans,
or they might want to manually create spans for their own custom code.

For detailed instructions, see [Manual instrumentation][manual].

## Logger MDC (Mapped Diagnostic Context) auto-instrumentation

It is possible to inject trace information like trace IDs and span IDs into your
custom application logs. For details, see [Logger MDC
auto-instrumentation](docs/logger-mdc-instrumentation.md).

## Troubleshooting

To turn on the agent's internal debug logging:

`-Dotel.javaagent.debug=true`

**Note**: These logs are extremely verbose. Enable debug logging only when needed.
Debug logging negatively impacts the performance of your application.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

### Maintainers

- This project is maintained by Alibaba
- Based on [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)

For more information about contributing and the maintainer role, see [CONTRIBUTING.md](CONTRIBUTING.md).

### Acknowledgments

This project is built upon the excellent work of the OpenTelemetry community. Special thanks to the original maintainers and contributors of [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation).

### Original OpenTelemetry Maintainers

The upstream OpenTelemetry Java Instrumentation project is maintained by:

- [Lauri Tulmin](https://github.com/laurit), Splunk
- [Trask Stalnaker](https://github.com/trask), Microsoft

### Original OpenTelemetry Approvers

- [Gregor Zeitlinger](https://github.com/zeitlinger), Grafana
- [Jack Berg](https://github.com/jack-berg), New Relic
- [Jason Plumb](https://github.com/breedx-splk), Splunk
- [Jay DeLuca](https://github.com/jaydeluca)
- [Jean Bisutti](https://github.com/jeanbisutti), Microsoft
- [John Watson](https://github.com/jkwatson), Cloudera
- [Jonas Kunz](https://github.com/JonasKunz), Elastic
- [Steve Rao](https://github.com/steverao), Alibaba
- [Sylvain Juge](https://github.com/SylvainJuge), Elastic

For more information about the approver role, see the [community repository](https://github.com/open-telemetry/community/blob/main/guides/contributor/membership.md#approver).

### Thanks to all contributors!

<a href="https://github.com/alibaba/loongsuite-java-agent/graphs/contributors">
  <img alt="Repo contributors" src="https://contrib.rocks/image?repo=alibaba/loongsuite-java-agent" />
</a>

And special thanks to all [OpenTelemetry Java Instrumentation contributors](https://github.com/open-telemetry/opentelemetry-java-instrumentation/graphs/contributors)!

[config-agent]: https://opentelemetry.io/docs/zero-code/java/agent/configuration/

[config-sdk]: https://opentelemetry.io/docs/languages/java/configuration/

[manual]: https://opentelemetry.io/docs/languages/java/instrumentation/#manual-instrumentation

[suppress]: https://opentelemetry.io/docs/zero-code/java/agent/disable/
