---
status: Accepted
date: 2026-04-22
deciders: Groep C1
---

# AD: Technologiestack voor de communicatiemodule

## Context and Problem Statement

De communicatiemodule moet berichten ontvangen, verwerken, valideren (HL7/FHIR), versturen via diverse providers, logging/monitoring ondersteunen en voldoen aan strenge beveiligingseisen (AES-256, TLS 1.3). De keuze van de technologiestack moet zowel de functionele als niet-functionele eisen ondersteunen en passen bij de capaciteiten van het team.

## Decision Drivers

* Ondersteuning voor HL7 FHIR-standaarden
* Beveiliging (versleuteling, geheimenbeheer)
* Schaalbaarheid en onafhankelijke verwerking (queueing, retry)
* Monitoring via OpenTelemetry en real-time dashboard
* Containeriseerbaar (Docker)
* Teamkennis en productiviteit

## Considered Options

1. **Java + Spring Boot + RabbitMQ + PostgreSQL + Prometheus/Grafana**
2. **Node.js + Express + BullMQ + MongoDB + OpenTelemetry**
3. **Python + FastAPI + Celery + Redis + PostgreSQL + Prometheus**

## Decision Outcome

Chosen option: **Optie 1 — Java + Spring Boot + RabbitMQ + PostgreSQL + Prometheus/Grafana**, omdat dit de meest robuuste combinatie is voor HL7/FHIR-integratie, sterke beveiligingsbibliotheken biedt, en breed wordt toegepast in zorgintegraties.

### Consequences

**Good:**
- Spring Boot uitgebreide ondersteuning heeft voor REST, JPA, en beveiliging (Spring Security).
- RabbitMQ betrouwbare queueing en retry-mechanismen biedt bij providerstoringen.
- PostgreSQL geschikt is voor gestructureerde data en encryptie op kolomniveau ondersteunt (AES-256).
- Prometheus + Grafana voldoen aan de OpenTelemetry- en dashboardeisen.

**Bad:**
- Java een zwaardere runtime heeft dan Node.js of Python.
- Meer boilerplate-code nodig is dan in Python.

## More Information

* Secrets management: HashiCorp Vault of Spring Cloud Config met encryptie.
* FHIR-verwerking: HAPI FHIR-bibliotheek.
* Monitoring: OpenTelemetry Java agent + Prometheus exporter.
* De module wordt verpakt als Docker-container.