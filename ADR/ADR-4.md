---
status: Accepted
date: 2026-05-06
deciders: Groep C1
---

# AD: Asynchrone verwerking van gepolde afspraken via RabbitMQ

## Context and Problem Statement

In ADR-3 is vastgesteld dat de communicatiemodule afspraakdata ophaalt via periodieke REST API polling (elke minuut per tenant). Dit ADR legt vast hoe de gepolde afspraken vervolgens worden verwerkt: hoe de `PollingJob` afspraken doorzet naar de verwerkingslaag, hoe voorkomen wordt dat dezelfde afspraak dubbel wordt verwerkt bij opeenvolgende polls, en hoe notificatieverzending ontkoppeld blijft van de polling-cyclus.

## Decision Drivers

* **Ontkoppeling**: De polling-cyclus mag niet worden geblokkeerd door trage providers of lange verwerkingstijden.
* **Betrouwbaarheid**: Geen enkel afspraakevent mag verloren gaan bij tijdelijke storingen in de verwerkingslaag.
* **Idempotency**: Dezelfde afspraak wordt elke poll-ronde opnieuw gezien; er mag slechts één notificatie per afspraak per tijdvenster worden verstuurd.
* **Multitenancy**: Polling en verwerking moeten per tenant gescheiden zijn zodat organisaties elkaar niet beïnvloeden.
* **Onderhoudbaarheid**: De verwerkingslaag moet onafhankelijk zijn van de polling-laag, zodat nieuwe providers of verwerkingsstappen kunnen worden toegevoegd zonder de `PollingJob` te wijzigen.

## Considered Options

1. **Asynchrone verwerking via RabbitMQ**: De `PollingJob` publiceert nieuwe afspraken als events naar een RabbitMQ-queue. Een aparte `NotificationConsumer` verwerkt de events asynchroon, inclusief retry en dead-letter-afhandeling.
2. **Synchrone verwerking direct in de PollingJob**: De job verwerkt en verzendt de notificatie direct binnen dezelfde poll-iteratie, zonder queue.
3. **Database-driven scheduling**: De job slaat gepolde afspraken op in de database; een aparte scheduler-thread leest de tabel en verstuurt notificaties op het juiste moment.

## Decision Outcome

Gekozen optie: **Optie 1: Asynchrone verwerking via RabbitMQ**, omdat dit de polling-cyclus volledig ontkoppelt van de verzendlaag, betrouwbare retry-afhandeling biedt via een dead-letter queue, en de verwerkingslaag onafhankelijk uitbreidbaar maakt.

### Hoe werkt de polling-verwerking?

De `PollingJob` draait via `@Scheduled` elke minuut. Per actieve tenant roept hij de OpenMRS REST API aan met een `lastUpdated`-filter op basis van de `lastPolledAt`-timestamp die per tenant in PostgreSQL wordt bijgehouden. Voor elk ontvangen afspraakinzicht:

1. De `IdempotencyService` controleert of het event al eerder is verwerkt via een atomische `INSERT INTO processed_events (event_id)`. Bij een `DuplicateKeyException` wordt het event stilzwijgend overgeslagen.
2. Nieuwe events worden gepubliceerd naar de RabbitMQ `appointment.events` exchange als `NotificationQueueMessage`.
3. Na een succesvolle poll-ronde wordt `lastPolledAt` per tenant bijgewerkt in PostgreSQL.

### Hoe werkt de idempotency?

Elk gepold afspraakinzicht krijgt een unieke `eventId` (samengesteld uit `appointmentId` + `lastModified`-timestamp). De `IdempotencyService` voert een atomische insert uit. Omdat de database de serialisatie afhandelt, werkt dit ook correct bij meerdere gelijktijdige instanties van de module. Events worden maximaal 30 dagen bewaard in de `processed_events`-tabel en daarna opgeschoond.

### Hoe werkt de asynchrone verwerking?

De `NotificationConsumer` leest van de RabbitMQ-queue en verwerkt elk event onafhankelijk van de polling-cyclus. Bij een fout (bijv. provider onbereikbaar) past de consumer exponentiële backoff toe (5s → 30s → 2m → 5m → 10m); na het maximum aantal pogingen wordt het event doorgestuurd naar `queue.dlq` voor handmatige inspectie.

### Consequences

**Good:**
- De `PollingJob` is lichtgewicht en snel: hij haalt data op en publiceert naar de queue zonder te wachten op verzending.
- Trage of falende providers blokkeren de polling-cyclus niet.
- De idempotency-laag garandeert dat patiënten nooit twee notificaties ontvangen voor dezelfde afspraak, ook niet bij herhaalde polls.
- De `NotificationConsumer` en polling-job zijn volledig ontkoppeld; nieuwe consumers of providers kunnen worden toegevoegd zonder de `PollingJob` te wijzigen.
- De `lastPolledAt`-timestamp per tenant maakt polling efficiënt: alleen wijzigingen sinds de vorige poll worden opgehaald.
- Bij eerste koppeling van een nieuwe tenant wordt een initiële volledige synchronisatie uitgevoerd vanaf een configureerbare startdatum.

**Bad:**
- De polling-interval (standaard 60 seconden) bepaalt de maximale vertraging waarmee een afspraakwijziging de notificatiequeue bereikt. Dit is acceptabel voor notificaties die 24 uur en 1 uur van tevoren worden verstuurd.
- De `PollingJob` belast de OpenMRS REST API periodiek. Bij een groot aantal tenants moeten polling-intervals zorgvuldig worden afgestemd; jitter wordt toegepast om gelijktijdige requests te spreiden.
- Idempotency vereist een persistente `processed_events`-tabel en expliciete opschoning na de retentieperiode.

## More Information

* De `NotificationQueueMessage` is een fat event: het bevat `appointmentId`, `patientId`, `scheduledTime`, `organizationId`, `location` en `instructions`, zodat de consumer geen extra synchrone call naar OpenMRS hoeft te doen.
* Retry-beleid voor de `NotificationConsumer`: 5 pogingen met exponentiële backoff; daarna doorsturen naar `queue.dlq`.
* De polling-interval is configureerbaar via `polling.interval-ms` in `application.yml` (standaard: 60000 ms).
* Jitter wordt toegepast per tenant zodat poll-requests niet gelijktijdig plaatsvinden.
* De `processed_events`-tabel wordt geïndexeerd op `processed_at` voor efficiënte opschoning; retentievenster is 30 dagen.
* Zie ADR-3 (polling als integratiemethode), ADR-2 (technologiestack) en ADR-8 (provider-factory) voor de besluiten waarop dit ADR voortbouwt.
