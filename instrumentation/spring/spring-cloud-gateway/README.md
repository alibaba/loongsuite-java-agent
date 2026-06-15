# Settings for the Spring Cloud Gateway instrumentation

| System property                                                          | Type    | Default        | Description                                                                                                                |
|--------------------------------------------------------------------------|---------|----------------|----------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.spring-cloud-gateway.experimental-span-attributes` | Boolean | `false`        | Enable the capture of experimental span attributes.                                                                        |
| `otel.instrumentation.spring-cloud-gateway.http-route-format`            | String  | `path-pattern` | Format for `http.route` on WebFlux spans: `path-pattern` (e.g. `/api/**`) or `route-id`. Path-pattern requires SCG 3.0.5+. |
