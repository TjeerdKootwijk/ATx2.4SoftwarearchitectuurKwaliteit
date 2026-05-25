---
status: Accepted
date: 2026-05-06
deciders: Groep C1
---

# AD: Webhook-ontvangst met asynchrone verwerking en fallback naar REST polling

## Context and Problem Statement

De communicatiemodule moet afspraakwijzigingen van OpenMRS ontvangen om op het juiste moment notificaties te versturen (24 uur en 1 uur voor de afspraak). In ADR-3 is vastgesteld dat webhooks het primaire integratiepad zijn, met een fallback naar REST API polling. Dit ADR legt vast hoe dat mechanisme concreet wordt geïmplementeerd: hoe de webhook-ontvanger is opgezet, hoe de fallback wordt getriggerd, en hoe voorkomen wordt dat een event dubbel wordt verwerkt wanneer zowel de webhook als de polling hetzelfde event aanleveren.

## Decision Drivers

* **Beschikbaarheid van OpenMRS**: OpenMRS mag geen blokkerende time-outs ervaren door trage verwerking van notificaties.
* **Betrouwbaarheid**: Geen enkel afspraakevent mag verloren gaan, ook niet bij tijdelijke downtime van de communicatiemodule.
* **Idempotency**: Wanneer een event via twee paden binnenkomt (webhook én polling), mag er slechts één notificatie worden verstuurd.
* **Multitenancy**: De fallback-polling moet per tenant afzonderlijk worden bijgehouden, zodat organisaties elkaar niet beïnvloeden.
* **Onderhoudbaarheid**: De ontvangstlaag moet onafhankelijk zijn van de verwerkingslaag, zodat nieuwe providers of verwerkingsstappen kunnen worden toegevoegd zonder de webhook-controller te wijzigen.

## Considered Options

1. **Synchrone verwerking in de webhook-handler** — De controller verwerkt en verzendt de notificatie direct binnen dezelfde HTTP-aanroep, zonder queue.
2. **Asynchrone ontvangst via RabbitMQ + periodieke polling als fallback** — De controller plaatst het event op een queue en retourneert direct `202 Accepted`. Een aparte `PollingJob` haalt periodiek gemiste events op via de OpenMRS REST API.
3. **Alleen polling, geen webhooks** — De module polt continu de OpenMRS REST API, zonder gebruik te maken van webhooks.

## Decision Outcome

Gekozen optie: **Optie 2 — Asynchrone ontvangst via RabbitMQ met periodieke REST-polling als fallback**, omdat dit de beschikbaarheid van OpenMRS volledig ontziet, betrouwbare event-ontvangst garandeert ook bij tijdelijke uitval, en de ontvangstlaag volledig ontkoppelt van de verwerkingslaag.

### Hoe werkt het webhook-pad?

Een OpenMRS-instantie stuurt een `POST /api/events/appointment` naar de communicatiemodule bij elke afspraakwijziging. De `WebhookController` valideert de HMAC-SHA256-handtekening, bouwt een `AppointmentChangedEvent` (fat event met alle benodigde velden), voert een idempotency-check uit op `eventId`, publiceert het event naar de RabbitMQ `appointment.events` exchange, en retourneert direct `202 Accepted` — zonder te wachten op verwerking of verzending.

### Hoe werkt de fallback-polling?

Een `PollingJob` draait via `@Scheduled` elke vijf minuten. Per actieve tenant roept hij de OpenMRS REST API aan met een `since`-parameter op basis van de `lastPolledAt`-timestamp die per tenant in PostgreSQL wordt bijgehouden. Ontvangen events worden langs dezelfde idempotency-check geleid als webhooks. Nieuwe events worden gepubliceerd naar RabbitMQ; events die al via het webhookpad zijn binnengekomen worden stilzwijgend overgeslagen. Na een succesvolle poll-ronde wordt de `lastPolledAt`-timestamp per tenant bijgewerkt.

### Hoe werkt de idempotency?

Elk `AppointmentChangedEvent` heeft een unieke `eventId`. De `IdempotencyService` voert een atomische `INSERT INTO processed_events (event_id) VALUES (?)` uit. Bij een `DuplicateKeyException` is het event al eerder verwerkt en wordt het overgeslagen. Dit werkt correct bij meerdere gelijktijdige instanties van de module, omdat de database de serialisatie afhandelt. Events worden maximaal 30 dagen bewaard in de `processed_events`-tabel; daarna kunnen ze worden opgeschoond.

### Consequences

**Good:**
- OpenMRS ontvangt altijd een directe `202 Accepted` zonder te wachten op verwerking of verzending.
- Gemiste webhooks — door tijdelijke downtime van de module of netwerkstoringen — worden automatisch opgepikt door de polling-fallback.
- De idempotency-laag garandeert dat patiënten nooit twee notificaties ontvangen voor dezelfde afspraakwijziging, ongeacht via welk pad het event binnenkomt.
- De webhook-controller en polling-job zijn volledig ontkoppeld van de verwerkingslaag; nieuwe consumers kunnen worden toegevoegd zonder deze componenten te wijzigen.
- De `lastPolledAt`-timestamp per tenant maakt de polling efficiënt: er wordt alleen gevraagd naar wijzigingen die de module nog niet heeft gezien.
- Bij de eerste koppeling van een nieuwe tenant polt de job eenmalig de volledige afsprakenhistorie, waarna het webhook-pad overneemt.

**Bad:**
- De polling-interval (standaard vijf minuten) bepaalt de maximale vertraging bij een gemist webhook-event. Dit is acceptabel voor notificaties die 24 uur en 1 uur voor de afspraak worden verstuurd.
- De `PollingJob` belast de OpenMRS REST API periodiek. Bij een groot aantal tenants moet het interval worden afgestemd op de capaciteit van OpenMRS-instanties.
- Idempotency vereist een persistente `processed_events`-tabel en expliciete opschoning na de retentieperiode.
- De HMAC-handtekeningvalidatie vereist dat per tenant een gedeeld secret veilig wordt opgeslagen (via HashiCorp Vault) en geconfigureerd in OpenMRS.

## More Information

* De `AppointmentChangedEvent` is een fat event: het bevat `appointmentId`, `patientId`, `scheduledTime`, `organizationId`, `location` en `instructions`, zodat downstream consumers geen extra synchrone call naar OpenMRS hoeven te doen.
* Retry-beleid voor de `NotificationConsumer`: 5 pogingen met exponentiële backoff (5s → 30s → 2m → 5m → 10m via `SimpleRetryPolicy`); daarna doorsturen naar `queue.dlq` voor handmatige inspectie.
* De polling-interval is configureerbaar via `polling.interval-ms` in `application.yml` (standaard: 300000 ms).
* De `processed_events`-tabel wordt geïndexeerd op `processed_at` voor efficiënte opschoning; retentievenster is 30 dagen.
* Een follow-up ADR over de `NotificationConsumer` en de provider-abstractielaag (Strategy-patroon voor SwiftSend, LegacyLink, AsyncFlow en SecurePost) is gewenst vóór productie-deployment.
* Zie ook ADR-3 (integratiemethode), ADR-2 (technologiestack) en ADR-LES-MARK-ABSTRACTIE (messaging-strategie) voor de besluiten waarop dit ADR voortbouwt.
