---
status: Accepted
date: 2026-05-21
deciders: Groep C1
---

# AD: Observability-stack voor de communicatiemodule

## Context and Problem Statement

De communicatiemodule moet real-time inzicht geven aan OpenMRS-beheerders: status van verstuurde en mislukte berichten, throughput, foutmeldingen en applicatiegezondheid. De stack moet containeriseerbaar zijn, self-hosted draaien en aansluiten op de bestaande Java + Spring Boot + RabbitMQ-stack (ADR-2).

## Decision Drivers

* Real-time inzicht in berichtstatus en fouten
* Alle drie observability-pijlers: metrics, logs en traces
* Containeriseerbaar en self-hosted (geen SaaS)
* Integratie met Spring Boot Actuator, RabbitMQ en OpenTelemetry Java agent

## Considered Options

1. **OpenTelemetry + Prometheus + Grafana LGTM-stack**: OpenTelemetry Java agent voor instrumentatie, Prometheus voor metrics, Loki/Tempo/Mimir voor logs/traces, alles via één `grafana/otel-lgtm`-container.
2. **ELK-stack**: Elasticsearch + Logstash + Kibana + Metricbeat. Meerdere zware containers, geen ingebouwde tracing.
3. **Datadog**: Commerciële SaaS met doorlopende kosten per host. Conflicteert met self-hosted eis.
4. **Jaeger + Prometheus + Grafana**: Geen geïntegreerde log-aggregatie; vereist alsnog Loki.

## Decision Outcome

Gekozen optie: **Optie 1: OpenTelemetry + Prometheus + Grafana LGTM-stack**, omdat deze alle drie de observability-pijlers dekt in een self-hosted setup, naadloos integreert met de bestaande stack, en via één container eenvoudig te deployen is.

### Stack inrichting

**Metrics** via Prometheus (scrape interval 5s):
- `job: spring-app`: scrapet `app:8080/actuator/prometheus`
- `job: rabbitmq`: scrapet `rabbitmq:15692/metrics` (aggregaat)
- `job: rabbitmq-per-queue`: scrapet `rabbitmq:15692/metrics/per-object` (per queue, voor DLQ-monitoring)

Custom Micrometer-tellers in `RabbitMQConsumer`:
- `notifications_sent_total{status="success"}`: telt bij elke succesvolle verzending
- `notifications_sent_total{status="failed"}`: telt bij elke mislukte verzending

**Logs** via OpenTelemetry Java agent als OTLP naar Loki. Dashboard toont errors/warnings apart en alle logs.

**Traces** via OpenTelemetry Java agent naar Tempo, beschikbaar via Grafana Explore.

### Vastgelegde metrics

| Categorie | Metric | Doel |
|-----------|--------|------|
| Berichten | `notifications_sent_total{status="success/failed"}` | Succesvolle en mislukte notificaties |
| Berichten | `rabbitmq_queue_messages{queue="notification.dead-letter.queue"}` | DLQ-diepte |
| Throughput | `rate(rabbitmq_channel_messages_published_total[1m])` | Berichten per seconde |
| Fouten | `increase(http_server_requests_seconds_count{status=~"[45].."}[1h])` | HTTP 4xx/5xx fouten |
| Applicatie | JVM heap-gebruik, CPU-gebruik | Gezondheid applicatieproces |

### Consequences

**Good:**
- Alle drie observability-pijlers beschikbaar via één Grafana-instantie.
- OpenTelemetry Java agent instrumenteert automatisch; alleen custom business-metrics vereisen code.
- `notifications_sent_total` telt op moment van daadwerkelijke verzending, geen timing-problemen.
- LGTM-stack draait als één container naast de overige services.

**Bad:**
- `grafana/otel-lgtm` is bedoeld voor ontwikkel-/testomgevingen; productie vereist losse services.
- Twee datapaden voor metrics: Prometheus scraping én OTLP via OpenTelemetry agent.

## More Information

- Prometheus-configuratie: `prometheus.yml`; dashboard: `grafana/dashboards/rabbitmq.json`
- Dashboard herlaadt automatisch via file-provisioning (`updateIntervalSeconds: 10`)
- Zie ADR-2 (technologiestack) en ADR-4 (retry/DLQ-beleid)
