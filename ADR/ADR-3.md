---
status: Accepted
date: 2026-04-22
deciders: Groep C1
---

# AD: Integratiemethode tussen OpenMRS en de communicatiemodule

## Context and Problem Statement

De communicatiemodule moet afspraakdata ontvangen van OpenMRS om notificaties te kunnen versturen. De integratie moet meerdere OpenMRS-instanties ondersteunen, bestand zijn tegen downtime, en aansluiten op de HL7/FHIR-standaard. Er moet bepaald worden via welk mechanisme de module de benodigde data verkrijgt.

## Decision Drivers

* Ondersteuning voor meerdere OpenMRS-organisaties (multitenancy)
* Robuustheid bij netwerk of providerstoringen
* Naleving van HL7/FHIR-standaard
* Minimale aanpassingen aan bestaande OpenMRS-installaties
* Traceerbaarheid en logging

## Considered Options

1. **OpenMRS REST API (polling)** — De module haalt periodiek afspraken op via de OpenMRS REST API.
2. **OpenMRS Webhooks/Events** — OpenMRS stuurt bij afspraakwijzigingen een HTTP-request naar de module.
3. **FHIR-server als tussenpersoon** — OpenMRS synchroniseert naar een FHIR-server, de module leest van de FHIR-server.
4. **Message broker (zoals Kafka of RabbitMQ)** — OpenMRS publiceert afspraakevents naar een gedeelde broker.

## Decision Outcome

Gekozen optie: Optie 2 - OpenMRS Webhooks/Events + fallback naar REST API polling, omdat dit de laagste latency biedt een event-driven architectuur ondersteunt, en robuust kan worden gemaakt met een retry en fallback-mechanisme.

### Consequences

**Good:**
- Notificaties direct kunnen worden verwerkt na een afspraakwijziging.
- Omdat de module niet voortdurend hoeft te pollen (minder belasting op OpenMRS).
- Bij tijdelijke storingen van de module de webhooks opnieuw worden verzonden (afhankelijk van OpenMRS-ondersteuning).

**Bad:**
- Omdat OpenMRS standaard geen uitgebreid webhook-mechanisme heeft (aanpassing of extra module nodig).
- Omdat de module een fallback-mechanisme moet implementeren (polling) voor gemiste events.
- Omdat webhooks minder geschikt zijn voor historische synchronisatie bij eerste koppeling.

### Fallback-strategie

Bij eerste koppeling en bij geconstateerde missing events gebruikt de module de OpenMRS REST API om vertraagd afspraken op te halen. Dit garandeert dat er geen afspraaknotificaties worden gemist.

## More Information

* Voor FHIR-compliance kan OpenMRS een FHIR REST endpoint aanbieden (OpenMRS FHIR module).
* De module implementeert een Status behoudend verwerking van webhooks om dubbele notificaties te voorkomen.
* Bij herhaalde fouten van een specifieke provider worden berichten in RabbitMQ in een dead letter queue geplaatst voor handmatige inspectie.
* De integratie wordt gedocumenteerd voor technisch beheerders, inclusief voorbeeldauthenticatie (API-keys, OAuth2 client credentials).