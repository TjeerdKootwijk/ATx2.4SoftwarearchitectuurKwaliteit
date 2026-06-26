# Performance test — notificatie-pipeline

Deze map bevat een [Apache JMeter](https://jmeter.apache.org/) load test die de **volledige
notificatie-pipeline** belast over **meerdere tenants** en **meerdere providers**:

```
JMeter  ──POST /api/notifications/test──►  Spring Boot app
                                               │ publiceert
                                               ▼
                                          RabbitMQ (notification.queue)
                                               │ consumeert
                                               ▼
                                          RabbitMQConsumer
                                               │ verstuurt
                                               ▼
                                  Messaging provider (SwiftSend / SecurePost /
                                  LegacyLink / AsyncFlow → fakeMessageProvider)
```

Elke request zet één bericht in de queue. De consumer haalt het eruit en verstuurt het via de
gekozen provider, waarna het resultaat (success/failed) wordt weggeschreven naar de database en
geteld in de `notifications_sent_total` metric. Zo meet je niet alleen de HTTP-laag maar de
**doorvoer van de hele keten**.

---

## Bestanden

| Bestand | Functie |
|---|---|
| `loadtest.py` | Python load-test script (geen install nodig behalve `requests`) |
| `pipeline-loadtest.jmx` | JMeter-testplan (zelfde test, vereist JMeter) |
| `tenants-providers.csv` | 4 tenants × 4 providers = 16 combinaties die round-robin worden gebruikt |

Er zijn twee manieren om de test te draaien die exact hetzelfde belasten: de **Python-variant**
(lichtgewicht, aanbevolen als je geen JMeter wilt installeren) en het **JMeter-plan**. Kies er één.

---

## Optie A — Python (geen install)

Vereist alleen Python 3.9+ en `requests` (`pip install requests`). Draai vanuit deze map:

```bash
python loadtest.py                              # 50 workers, 120s (defaults)
python loadtest.py --threads 200 --duration 300 # zwaardere run
python loadtest.py --host localhost --port 8443 # andere doelhost/poort
```

Het script print throughput (req/s), error %, latency-percentielen (p50/p90/p95/p99) en het aantal
requests per provider. Parameters: `--threads`, `--duration`, `--host`, `--port`, `--protocol`,
`--csv`. Ctrl+C stopt vroegtijdig en toont alsnog de resultaten.

---

## Vereisten

1. **De applicatiestack draait.** Vanuit de projectroot:
   ```bash
   docker-compose up --build
   ```
   Wacht tot `docker-compose ps` alle containers als `running`/`healthy` toont. De app luistert
   op **https://localhost:8443** (TLS, self-signed certificaat — JMeter valideert dit niet).

2. **JMeter geïnstalleerd** (versie 5.5+). Download via https://jmeter.apache.org/download_jmeter.cgi,
   pak uit, en gebruik `bin/jmeter` (Linux/Mac) of `bin\jmeter.bat` (Windows).
   Vereist een geïnstalleerde JDK (de app gebruikt al Java 21).

---

## Optie B — JMeter

### CLI (aanbevolen, met HTML-rapport)

Dit is de manier om écht te meten; de GUI is alleen voor het bouwen/debuggen van het plan.
Voer uit **vanuit deze `performance/` map** (zodat het CSV-pad klopt):

**Windows (PowerShell):**
```powershell
jmeter -n -t pipeline-loadtest.jmx -l results.jtl -e -o report
```

**Linux / Mac:**
```bash
jmeter -n -t pipeline-loadtest.jmx -l results.jtl -e -o report
```

- `-n` = non-GUI mode
- `-l results.jtl` = ruwe resultaten
- `-e -o report` = genereer een HTML-dashboard in de map `report/` (open `report/index.html`)

> `results.jtl` en `report/` moeten leeg/niet-bestaand zijn vóór een run. Verwijder ze tussen runs.

### Belastingsparameters aanpassen

Alle parameters zijn overschrijfbaar met `-J`:

| Parameter | Default | Betekenis |
|---|---|---|
| `threads` | `50` | aantal gelijktijdige virtuele gebruikers |
| `rampup` | `30` | seconden om alle threads op te starten |
| `duration` | `120` | testduur in seconden |
| `host` | `localhost` | doelhost |
| `port` | `8443` | doelpoort |
| `protocol` | `https` | http of https |

Voorbeeld — 200 gebruikers, 60s opstart, 5 minuten draaien:
```bash
jmeter -n -t pipeline-loadtest.jmx -l results.jtl -e -o report \
  -Jthreads=200 -Jrampup=60 -Jduration=300
```

---

### GUI (voor opbouwen/debuggen)

```bash
jmeter -t performance/pipeline-loadtest.jmx
```
Druk op de groene start-knop. Bekijk **Summary Report** en **Aggregate Report**.
Draai grote tests niet in GUI-mode — dat vertekent de meting.

---

## Wat te bekijken

**In het JMeter HTML-rapport (`report/index.html`):**
- *Throughput* (requests/sec) — hoeveel berichten de app per seconde in de queue accepteert
- *Response Times* (gemiddeld, **p90/p95/p99**) van het `/test`-endpoint
- *Error %* — moet 0% zijn (assertion verwacht HTTP 202)

**De échte pipeline-doorvoer (verwerking door de consumer/provider) zie je niet in JMeter zelf,
maar in de monitoring** — want JMeter meet alleen tot "in queue":

| Wat | Waar |
|---|---|
| Queue-diepte & consume rate | RabbitMQ beheer → http://localhost:15672 (guest/guest), queue `notification.queue` |
| Verstuurde notificaties per provider | Grafana → http://localhost:3000 (admin/admin), metric `notifications_sent_total{status,provider}` |
| Dead-letter (mislukte berichten) | RabbitMQ queue `notification.dead-letter.queue` |

Een gezonde test: JMeter accepteert N req/s met 0% errors, en in RabbitMQ loopt de queue niet
structureel vol (consume rate ≈ publish rate). Loopt de queue wél op, dan is de consumer/provider
de bottleneck — precies wat je met een pipeline-test wilt ontdekken.

---

## Tenants & providers aanpassen

Pas `tenants-providers.csv` aan om andere tenants, providers of telefoonnummers te testen.
Kolommen: `tenantId,provider,recipient`. Geldige providers: `SWIFTSEND`, `SECUREPOST`,
`LEGACYLINK`, `ASYNCFLOW`. De rijen worden round-robin verdeeld over alle threads.

> Alle vier de providers werken zodra de stack draait. `ASYNCFLOW` gebruikt
> `PROVIDERS_ASYNCFLOW_API_KEY` uit `.env` (al ingevuld); de andere providers hebben hun config
> via docker-compose. Mochten provider-credentials ontbreken, dan slaagt de HTTP-request nog steeds
> met 202 en treedt de fout pas op in de consumer (zichtbaar als `failed` in `notifications_sent_total`).
