# Performance rapport — OpenMRS Communicatiemodule

**Project:** ATx2.4 Softwarearchitectuur & Kwaliteit
**Onderwerp:** Doorvoer- en schaalbaarheidsanalyse van de notificatie-pipeline
**Datum:** 25 juni 2026
**System under test (SUT):** Spring Boot communicatiemodule (commit op branch `main`)

---

## 1. Managementsamenvatting

De notificatie-pipeline is belast om te bepalen hoeveel afspraaknotificaties de module
per seconde kan verwerken en waar de doorvoer vastloopt. De test legde een duidelijke
keten van knelpunten bloot:

1. **De HTTP-laag (producer) is snel** — de module accepteert ruim **200 berichten/seconde**
   in de wachtrij.
2. **De consumer-kant was de bottleneck.** In de uitgangssituatie verwerkte de module
   slechts **~0,4 berichten/seconde**, omdat de RabbitMQ-listener op één consumer-thread
   draaide (standaardinstelling). Bij belasting liep de wachtrij op tot **~21.000 berichten**,
   waarna RabbitMQ de producers blokkeerde (backpressure) en de HTTP-latency explodeerde
   (gemiddeld 2,3 s, uitschieter ~898 s).
3. **Na het instellen van consumer-concurrency** (8–16 parallelle consumers) steeg de doorvoer
   naar **~3,0 berichten/seconde gemiddeld met pieken tot ~20/s** — een verbetering van
   **factor ~7,5** gemiddeld en **~25** in piek.
4. **De fix legde een tweede knelpunt bloot:** de uitgaande provider-aanroepen lopen op de
   standaard Reactor Netty connection-pool, die onder parallelle belasting verzadigt en de
   doorvoer in golven van ~45 seconden afknijpt.

**Belangrijkste aanbeveling:** voer de consumer-concurrency-instelling door (gedaan) en
optimaliseer vervolgens de WebClient connection-pool van de providers. Daarmee komt de
sustained doorvoer naar verwachting richting de gemeten piek van ~20 msg/s.

---

## 2. Doel en scope

### Doel
Vaststellen wat de maximale doorvoer van de end-to-end notificatie-pipeline is, of de
architectuur meerdere tenants en providers gelijktijdig aankan, en waar de doorvoer
vastloopt onder belasting.

### Scope — de geteste pipeline

```mermaid
flowchart LR
    A[Load test<br/>POST /api/notifications/test] --> B[Spring Boot app<br/>RabbitMQProducer]
    B --> C[(RabbitMQ<br/>notification.queue)]
    C --> D[RabbitMQConsumer]
    D --> E[MessagingProvider<br/>SwiftSend / SecurePost /<br/>LegacyLink / AsyncFlow]
    E --> F[fakeMessageProvider]
    D --> G[(PostgreSQL<br/>notification_log)]
```

Elke request publiceert één bericht naar RabbitMQ. De consumer haalt het bericht op,
verstuurt het via de gekozen provider en schrijft het resultaat (success/failed) naar de
database en de metric `notifications_sent_total`.

### Buiten scope
- De `PollingJob` (OpenMRS-polling) — stond uitgeschakeld (`app.polling.enabled=false`);
  de pipeline is rechtstreeks gevoed via het test-endpoint.
- De `NotificationScheduler` (24u/1u timing) — niet relevant voor doorvoermeting.
- Externe netwerklatency naar echte providers — er is een lokale fake-provider gebruikt.

---

## 3. Testopzet

### Omgeving
Alle componenten draaiden lokaal via `docker-compose` op één machine (Windows 11, Java 21
in de container). De app communiceert over HTTPS (TLS 1.3) op poort 8443.

### Gereedschap
| Instrument | Functie |
|---|---|
| `performance/loadtest.py` | HTTP load test — meet throughput en latency-percentielen van de producer-laag |
| `performance/measure_drain.py` | Meet de consumer-doorvoer (msg/s die de provider daadwerkelijk verstuurt) |
| `performance/pipeline-loadtest.jmx` | Gelijkwaardig JMeter-testplan |
| RabbitMQ Management API | Wachtrij-diepte (ready/unacked) en aantal consumers |
| Prometheus / Grafana | `notifications_sent_total`, queue-diepte over tijd |

> **Meetkeuze.** De HTTP-throughput meet alleen tot "in queue". De échte pipeline-doorvoer
> is de snelheid waarmee de consumer de wachtrij leegtrekt. Die is gemeten als delta van
> `notifications_sent_total` over de tijd, terwijl er een backlog in de wachtrij stond — los
> van eventuele HTTP-backpressure.

### Belastingsmodel — meerdere tenants en providers
De test verdeelt het verkeer over **4 tenants × 4 providers = 16 combinaties**
(zie `performance/tenants-providers.csv`):

| Tenants | Providers |
|---|---|
| test-ziekenhuis, amsterdam-umc, radboud-umc, erasmus-mc | SWIFTSEND, SECUREPOST, LEGACYLINK, ASYNCFLOW |

Daarmee wordt zowel de multi-tenant- als de multi-provider-werking onder gelijktijdige
belasting getest.

---

## 4. Resultaten

### 4.1 Producer-laag (HTTP load test)

Test: 50 gelijktijdige workers tegen `POST /api/notifications/test`.

| Metric | Waarde |
|---|---|
| Geaccepteerd (HTTP 202) | 20.202 berichten |
| Error % | 0,01 % |
| HTTP-throughput (korte burst, lege wachtrij) | ~178 req/s |
| Wachtrij-diepte op piek | **~20.955 berichten** |
| HTTP-latency gemiddeld | 2.305 ms |
| HTTP-latency p99 | 538 ms |
| HTTP-latency max | **897.952 ms (~15 min)** |

**Observatie.** De producer is snel, maar omdat de consumer het tempo niet bijhoudt, loopt
de wachtrij vol. RabbitMQ bereikt zijn memory-watermark en activeert flow-control: de
publishers worden geblokkeerd, waardoor individuele HTTP-aanroepen seconden tot minuten
blijven hangen en de throughput instort van ~178 naar ~21 req/s. Het grote verschil tussen
de p99 (538 ms) en de max (~898 s) toont dat de meeste requests snel zijn, maar een deel
volledig vastloopt op de backpressure.

### 4.2 Consumer-doorvoer — baseline (1 consumer)

Test: backlog opgebouwd, drain rate gemeten over ~112 s.

| Metric | Waarde |
|---|---|
| Actieve consumers | **1** |
| Prefetch (default) | 250 (komt overeen met "Messages Unacked: 250" in Grafana) |
| Gemiddelde drain rate | **0,4 msg/s** |
| Piek drain rate | 0,8 msg/s |
| Verwerkingstijd per bericht | ~2,4 s |

**Root cause.** Er was nergens listener-concurrency of prefetch geconfigureerd, dus de
`@RabbitListener` draaide op Spring's standaard van **één consumer-thread**. Berichten worden
strikt serieel verwerkt. Omdat het werk per bericht I/O-bound is (provider-HTTP-aanroep +
DB-write), staat die ene thread vrijwel continu te wachten.

### 4.3 Interventie — consumer-concurrency

Toegevoegd aan `src/main/resources/application.properties`:

```properties
spring.rabbitmq.listener.simple.concurrency=8
spring.rabbitmq.listener.simple.max-concurrency=16
spring.rabbitmq.listener.simple.prefetch=10
```

Onderbouwing: het werk is I/O-bound, dus meerdere consumers parallelliseren de wachttijd op
provider-aanroepen. Een lage prefetch verdeelt berichten eerlijk over de consumers in plaats
van dat enkele consumers de hele wachtrij claimen.

### 4.4 Consumer-doorvoer — na de fix (8–16 consumers)

| Metric | Baseline | Na fix | Verbetering |
|---|---|---|---|
| Actieve consumers | 1 | 8–10 | — |
| Gemiddelde drain rate | 0,4 msg/s | **3,0 msg/s** | **~7,5×** |
| Piek drain rate | 0,8 msg/s | **20,3 msg/s** | **~25×** |
| Backlog ~230 leeglopen | ~10 min | **~6 s** | **~100×** |

Een backlog die in de baseline ~10 minuten kostte, was na de fix in ongeveer 6 seconden
verwerkt.

### 4.5 Tweede bottleneck — provider connection-pool

De na-meting verliep **schokkerig**: perioden van ~40 s zonder voortgang, gevolgd door een
burst van ~20 msg/s, cyclisch met een periode van **~40–45 seconden** (bursts rond
t+41, t+82, t+127, t+168 en t+214 s).

**Root cause.** De provider-clients bouwen hun WebClient uit Spring's auto-configured
`WebClient.Builder` (zie `src/main/java/.../provider/swiftsend/SwiftSendClient.java`) en
gebruiken **niet** de in `WebClientConfig` geconfigureerde `webClient()`-bean. Daardoor
draaien de uitgaande aanroepen op de **standaard Reactor Netty connection-pool**. Onder 16
parallelle consumers verzadigt die pool; overige aanroepen wachten in de pending-acquire-queue
en worden in golven vrijgegeven. De waargenomen periode van ~45 s komt overeen met de
standaard `pendingAcquireTimeout` van Reactor Netty.

De bottleneck is dus verschoven: van **"één consumer-thread"** (opgelost) naar
**"de provider-connection-pool"**. De gemeten piek van ~20 msg/s laat zien dat de consumers
de doorvoer aankunnen; de uitgaande HTTP-laag knijpt de sustained doorvoer af.

---

## 5. Conclusies

1. **De architectuur is functioneel correct onder belasting:** multi-tenant en multi-provider
   verkeer wordt verwerkt met een verwaarloosbaar foutpercentage (0,01 %).
2. **De grootste schaalbaarheidswinst zit in consumer-concurrency.** Eén configuratie-wijziging
   gaf een doorvoerverbetering van factor ~7,5 gemiddeld.
3. **Zonder backpressure-bescherming is de producer een risico:** de module accepteert
   onbeperkt berichten, waardoor de wachtrij en het geheugen van RabbitMQ vollopen en
   publishers vastlopen.
4. **De uitgaande provider-laag is het volgende knelpunt.** De geconfigureerde WebClient-bean
   wordt niet gebruikt, waardoor de standaard connection-pool de sustained doorvoer beperkt.

---

## 6. Aanbevelingen (geprioriteerd)

| # | Aanbeveling | Verwacht effect | Status |
|---|---|---|---|
| 1 | Consumer-concurrency instellen (`concurrency=8`, `max-concurrency=16`, `prefetch=10`) | ~7,5× hogere doorvoer | **Doorgevoerd** |
| 2 | Provider-clients de geconfigureerde connector laten gebruiken én de connection-pool vergroten (`ConnectionProvider` met o.a. hogere `maxConnections`, kortere `pendingAcquireTimeout`) | Schokkerig patroon verdwijnt; sustained doorvoer richting ~20 msg/s | Aanbevolen |
| 3 | Concurrency, prefetch en pool-grootte op elkaar afstemmen na meting #2 | Optimale balans consumer ↔ provider | Aanbevolen |
| 4 | Producer-side backpressure / rate limiting overwegen (bv. publisher confirms + begrenzing) | Voorkomt geheugendruk en publisher-blocking bij pieken | Aanbevolen |
| 5 | Meting herhalen op productie-achtige hardware en met echte provider-latency | Realistischer capaciteitscijfer | Aanbevolen |

---

## 7. Beperkingen van het onderzoek

- Alle componenten draaiden op één lokale machine; consumer en provider concurreren om
  dezelfde CPU/IO. Op gescheiden infrastructuur kunnen de absolute cijfers hoger liggen.
- De provider is een lokale **fake** (`fakeMessageProvider`) die latency simuleert; echte
  providers hebben andere latency- en rate-limit-karakteristieken.
- De drain-metingen zijn uitgevoerd over vensters van ~110–245 s; langere runs geven stabielere
  gemiddelden.

---

## 8. Reproduceren

Vanuit de projectroot:

```bash
# 1. Stack starten
docker-compose up -d --build

# 2. (optioneel) wachtrij legen voor een schone meting
docker exec atx24-rabbitmq rabbitmqctl purge_queue notification.queue

# 3. HTTP load test
cd performance
python loadtest.py --threads 50 --duration 120

# 4. Consumer-doorvoer meten (voor/na een wijziging)
python measure_drain.py --backlog 3000 --label "baseline"
```

Zie [README.md](README.md) voor alle parameters en de JMeter-variant.

### Ruwe meetdata

| Run | Bestand / bron |
|---|---|
| HTTP load test | `loadtest.py` console-output (sectie 4.1) |
| Drain baseline | `measure_drain.py --label "baseline (concurrency=1)"` (sectie 4.2) |
| Drain na fix | `measure_drain.py --label "na fix (concurrency=8-16)"` (sectie 4.4) |
| Wachtrij & consumers | RabbitMQ Management API `GET /api/queues/%2F/notification.queue` |
| Verstuurd per provider | Prometheus `notifications_sent_total{provider,status}` |
