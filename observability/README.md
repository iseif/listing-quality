# Local AI Observability Stack

This stack runs Prometheus 3.12.0, Tempo 2.10.5, and Grafana 13.1.0. The Java applications continue to run on the host, which keeps local oMLX access simple.

## Start the backend

```bash
docker compose -f observability/compose.yaml up -d
```

Start the Listing Quality API with its model profile and observability enabled:

```bash
SPRING_PROFILES_ACTIVE=gemini,observability ./mvnw -pl listing-quality-service spring-boot:run
```

Start the Enrichment API in a second terminal:

```bash
SPRING_PROFILES_ACTIVE=observability ./mvnw -pl listing-quality-enrichment spring-boot:run
```

The `observability` profile controls trace export only. Both applications expose
`/actuator/prometheus` without it, so Prometheus scrapes succeed either way and only the Tempo
panels stay empty when the profile is absent.

The applications expect the provider credentials documented in the root README. Never put secrets in this Compose file or commit them to application configuration.

## Open the tools

| Tool | URL | Local credentials |
| --- | --- | --- |
| Grafana dashboard | http://localhost:3000/d/spring-ai-production-overview/spring-ai-production-overview | `admin` / `${GRAFANA_ADMIN_PASSWORD:-admin}` |
| Prometheus | http://localhost:9090 | none |
| Prometheus targets | http://localhost:9090/targets | none |
| Tempo API | http://localhost:3200 | none |
| OTLP HTTP | http://localhost:4318/v1/traces | none |

Grafana provisions the **Spring AI Production Overview** dashboard. Prometheus scrapes the two host applications on ports 8080 and 8081 every five seconds. Both targets must show `UP` on the Prometheus targets page before the dashboard can display application data. Tempo receives OTLP traces from both applications.

Stop the backend without deleting its named volumes:

```bash
docker compose -f observability/compose.yaml down
```

## Privacy defaults

The application telemetry uses closed, low-cardinality tags. It does not add listing IDs, titles, image URLs, prompts, tool arguments, tool results, provider messages, or API keys to metrics. Spring AI prompt and completion observation remains disabled. The Google Books client strips the complete query string from its traced URL, so search terms, ISBNs, and the API key do not reach Tempo.

The checked-in stack has no authentication or TLS and is for local development only. Do not expose ports 3000, 3200, 4318, or 9090 to an untrusted network.

## Cost estimates

The Prometheus rules use this price snapshot from 2026-07-21:

| Model | Input per million tokens | Output per million tokens |
| --- | ---: | ---: |
| Gemini 3.5 Flash | USD 1.50 | USD 9.00 |
| GPT-5.4 mini | USD 0.75 | USD 4.50 |

The dashboard values are estimates, not billing records. They exclude cached-input discounts, account contracts, tool fees, taxes, and provider billing reconciliation. Local inference has no provider token fee, but it still consumes hardware, electricity, engineering time, and operational capacity.

## Production hardening

For production, place management endpoints on a separate authenticated network, enable TLS, lower trace sampling, use persistent object storage, route alerts through Alertmanager, and put an OpenTelemetry Collector between applications and the telemetry backend. Set SLO thresholds from measured traffic instead of copying the educational examples in this repository.
