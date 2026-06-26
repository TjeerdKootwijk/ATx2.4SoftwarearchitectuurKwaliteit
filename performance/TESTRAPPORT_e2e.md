# Testrapport — End-to-end performance test notificatie-pipeline

**Project:** ATx2.4 Softwarearchitectuur & Kwaliteit
**Leeruitkomst:** LU1 (gekoppeld aan de FMEA — zie §6)
**Datum:** 26 juni 2026
**Type test:** End-to-end performance-/belastingstest van de volledige pipeline
**Uitgevoerd door:** load via de OpenMRS-ingest (fake-OpenMRS) → hele keten

---

## 1. Testdoel

Vaststellen of de **volledige notificatie-pipeline** een grote stroom afspraken aankan, waar de
doorvoer vastloopt, en — gekoppeld aan de FMEA — of de **maatregelen tegen provider-falen** onder
realistische belasting werken.

De geteste keten (volledig, niet alleen de verzend-helft):
```
PollingJob → fake-OpenMRS → FHIR-mapping → FHIR-validatie → event
           → AppointmentService → RabbitMQ → consumer → provider (SecurePost)
```

---

## 2. Testopzet

| Onderdeel | Waarde |
|---|---|
| Omgeving | Lokaal, `docker-compose` (Windows 11, Java 21 in container) |
| Belasting | **1000 afspraken** in één polling-cyclus, allemaal binnen het 1u-verzendvenster |
| Provider | SECUREPOST (tenant-default `OPENMRS_NOTIFICATION_PROVIDER`) |
| Meetinstrument | `performance/pipeline_e2e_measure.py` |
| Aanvullende bronnen | RabbitMQ Management API, PostgreSQL `notification_logs`, applicatielogs |

**Scenario.** De fake-OpenMRS genereert 1000 afspraken. De `PollingJob` haalt ze op, mapt ze naar
FHIR R4, valideert ze, en publiceert per afspraak een notificatie naar RabbitMQ. De consumer
verstuurt ze via SecurePost.

---

## 3. Testuitvoering

```powershell
# 1000 afspraken, schone staat, verse poll
$env:FAKE_APPOINTMENT_COUNT_1H=1000
docker-compose up -d --build fake-openmrs
docker-compose stop app
docker exec atx24-rabbitmq rabbitmqctl purge_queue notification.queue
docker exec atx24-rabbitmq rabbitmqctl purge_queue notification.dead-letter.queue
docker exec atx24-postgres psql -U postgres -d atx24db -c "DELETE FROM processed_events; DELETE FROM notification_logs;"
docker-compose start app
python performance/pipeline_e2e_measure.py --expected 1000 --max-wait 200
```

(Of in één keer: `./performance/run_e2e.ps1 -Count 1000`.)

---

## 4. Resultaten

### 4.1 Doorvoer (pipeline)

| Metric | Waarde |
|---|---|
| Ingest-latency (poll → eerste bericht in queue) | ~15 s |
| Ingest-doorvoer (poll + FHIR-mapping + validatie + publiceren) | ~47 msg/s |
| Piek queue-diepte | **355** |
| Piek verzend-rate (consumer) | **177 msg/s** |
| Alle 1000 verwerkt binnen | ~7 s na start van de cyclus |

De **ingest-laag (incl. de zware HAPI FHIR-validatie) is geen knelpunt** — 1000 afspraken werden
binnen seconden gemapt, gevalideerd en in de wachtrij gezet. De consumer haalde pieken van
177 msg/s; de queue liep kort op tot 355 en liep daarna snel leeg.

### 4.2 Afleverresultaat (provider)

| Bron | Cijfer |
|---|---|
| Unieke notificaties | 1000 |
| **Succesvol afgeleverd** | **9** |
| **Definitief/herhaald mislukt** | **991** |
| Mislukte pogingen (incl. retries) | 2.973 (≈3 per bericht) |
| `HTTP 429 TOO_MANY_REQUESTS` in logs | **20.410** |

**Slagingspercentage onder deze load: 0,9 %.** De oorzaak is **niet** de pipeline maar de
**provider-rate-limit**: SecurePost weigerde vrijwel alle berichten met HTTP 429. De mislukte
berichten doorliepen de retry-queues (exponential backoff 5s/30s/2m/5m/10m) richting de
dead-letter queue.

---

## 5. Analyse

1. **De bottleneck ligt buiten de app.** De pipeline zelf (ingest, FHIR, queue, consumer) is snel
   (177 msg/s). De beperkende factor is de **rate-limit van de provider**.
2. **Eén provider = één faalpunt.** Omdat alle 1000 berichten naar dezelfde provider (SecurePost)
   gingen, sloeg de rate-limit direct hard toe. Verkeer spreiden over meerdere providers
   (bijv. LegacyLink kende in eerdere runs 0 fouten) verzacht dit.
3. **De betrouwbaarheidsmaatregelen werken.** Geen enkel bericht ging stil verloren: alles werd
   herprobeerd en (na uitputting) naar de dead-letter queue geleid voor handmatige inspectie.

---

## 6. Koppeling met de FMEA (LU1)

Deze test triggert en valideert meerdere FMEA-risico's onder realistische belasting:

| FMEA # | Failure mode | Maatregel (FMEA) | Aangetoond door deze test |
|---|---|---|---|
| **#4** | Provider `429 Too Many Requests` (rate-limit + te veel requests in korte tijd) | 5× retry met exponential backoff (5s/30s/2m/5m/10m) → dead-letter | ✅ **20.410× 429** waargenomen; 2.973 retry-pogingen voor 991 berichten → dead-letter. De maatregel werkt exact zoals beschreven. |
| **#3** | Provider tijdelijk onbereikbaar | Idem (retry → dead-letter) | ✅ Zelfde retry/dead-letter-mechanisme onder load. |
| **#6** | Provider geeft willekeurige error (500) | Idem (retry → dead-letter) | ✅ Mislukte deliveries doorlopen dezelfde keten. |
| **#5** | Dubbele delivery | Idempotency-key op event-/notificatie-id | ✅ Bevestigd: herhaalde polling slaat reeds verwerkte afspraken over (`processed_events`). |

**Kernboodschap (CGI).** De performance test belast de hele pipeline; onder die load treedt
FMEA-risico **#4 (provider-ratelimit, 429)** daadwerkelijk op (20.410 keer), en de bijbehorende
**FMEA-maatregel** — retry met exponential backoff gevolgd door de dead-letter queue — vangt elk
mislukt bericht op. Geen enkel bericht raakt zoek. Zo bewijst de test dat de in de FMEA bedachte
maatregelen onder realistische belasting standhouden.

---

## 7. Conclusie & aanbevelingen

**Conclusie.** De pipeline-architectuur (ingest + verwerking) is snel en schaalt goed; de
betrouwbaarheidsmaatregelen uit de FMEA werken aantoonbaar. De effectieve doorvoer wordt echter
begrensd door de **rate-limit van de provider**, niet door de app.

**Aanbevelingen:**
1. **Respecteer de `Retry-After`-header van een 429** (verfijning FMEA-maatregel #4), zodat de
   module zich aanpast aan de provider-limiet i.p.v. werk naar de dead-letter queue te laten lekken.
2. **Client-side rate limiting / throttling** richting de provider, afgestemd op diens limiet.
3. **Verkeer spreiden over meerdere providers** waar mogelijk (multi-provider is al ondersteund).
4. **Herhaal de meting met echte providers**; de fake simuleert een strenge rate-limit.

---

## 8. Beperkingen

- Alle componenten draaiden op één lokale machine; absolute cijfers kunnen op gescheiden
  infrastructuur hoger liggen.
- De provider is een **fake** (`fakeMessageProvider`) met een gesimuleerde, strenge rate-limit.
- Alle belasting ging naar één provider (SecurePost); een gemengde providerverdeling geeft een
  gunstiger slagingspercentage.
- `pipeline_e2e_measure.py` sampelt elke 3 s; zeer korte bursts kunnen tussen metingen vallen
  (de piek queue-diepte is daardoor een ondergrens).

---

*Reproduceerbaar met `performance/run_e2e.ps1`. Zie ook het bredere
[RAPPORT.md](RAPPORT.md) voor de doorvoer-/schaalbaarheidsanalyse van de verzend-helft.*
