# Grafana Dashboard

De communicatiemodule heeft een ingebouwd Grafana dashboard waarmee je in realtime de status van berichten, de doorvoer en eventuele fouten kunt bewaken.

---

## Toegang

Start de module met `docker-compose up --build` en open daarna:

```
http://localhost:3000
```

Inloggegevens:
- Gebruikersnaam: `admin`
- Wachtwoord: `admin`

Het dashboard wordt automatisch geladen via file-provisioning. Je hoeft niets handmatig te importeren.

---

## Het dashboard vinden

Na het inloggen ga je naar:

**Dashboards > RabbitMQ / Communicatiemodule**

Of gebruik de zoekfunctie bovenin (het vergrootglas) en zoek op `RabbitMQ`.

<img width="2560" height="614" alt="image" src="https://github.com/user-attachments/assets/721cf77a-ba0b-45cd-a977-b8729d92202c" />

---

## Wat het dashboard toont

| Paneel | Wat het meet |
|---|---|
| Notificaties verstuurd | Aantal succesvolle notificaties in de geselecteerde periode |
| Mislukte notificaties | Aantal mislukte pogingen (en hoe vaak herproefd) |
| Dead-letter queue diepte | Aantal berichten dat na maximale herproberingen is gestrand |
| Berichten per seconde | Doorvoer van de RabbitMQ queue |
| HTTP fouten (4xx/5xx) | Foutieve verzoeken naar de webhook endpoint |
| JVM geheugengebruik | Gezondheid van de Spring Boot applicatie |

<img width="2560" height="1307" alt="image" src="https://github.com/user-attachments/assets/1a797b7f-b09e-4f7c-b9f3-55c967b46296" />
<img width="2180" height="1030" alt="image" src="https://github.com/user-attachments/assets/0de974a7-106a-4233-a3df-c365d140c168" />
<img width="2173" height="800" alt="image" src="https://github.com/user-attachments/assets/6de7f0ea-b22d-4b19-bf43-004c166eaa1d" />
<img width="2182" height="761" alt="image" src="https://github.com/user-attachments/assets/a84a080d-d30f-4ec1-ada9-d48a297f1b77" />

---

## Databronnen

Het dashboard gebruikt twee databronnen die automatisch worden geconfigureerd:

- **Prometheus** - metrics van RabbitMQ (queue-diepte, doorvoer) en van de Spring Boot applicatie (JVM, HTTP)
- **Loki** - logregels van de applicatie (errors en warnings zijn apart gefilterd)

Traces zijn beschikbaar via **Grafana Explore > Tempo**.

---

## Foutopsporing: een fout herleiden tot de regel + de ingevulde gegevens

Naast het RabbitMQ-dashboard is er een tweede dashboard **Foutopsporing / Error Tracing**.
Daarmee herleid je een fout helemaal terug naar de **precieze regel code** waar het misging,
**inclusief de ingevulde gegevens** die de fout veroorzaakten.

**Werkwijze:**

1. Open **Dashboards > Foutopsporing / Error Tracing**.
2. In het paneel **Foutlogs met stacktrace** klik je een foutregel open.
   - De stacktrace toont `Klasse.methode(Bestand.java:regelnummer)` — dát is de regel waar het vastliep.
   - In de details staat de **input-context**: `notification.id`, `tenant.id`, `provider`,
     `message.type` en `attempt` (pogingsnummer).
3. Klik op het veld **trace_id** → **Open trace in Tempo** om de volledige trace (alle spans)
   van dezelfde verwerking te bekijken.

Hoe het technisch werkt: bij het verwerken van een notificatie wordt de niet-PII input-context
in de [SLF4J MDC](https://www.slf4j.org/manual.html#mdc) gezet door
`NotificationDiagnosticContext`. De `RabbitMQConsumer` vangt fouten op de pijplijngrens en logt
de volledige exception (`log.error("...", e)`), waardoor de stacktrace mét de context naar Loki
gaat. De OpenTelemetry-agent voegt automatisch `trace_id`/`span_id` toe voor de koppeling met Tempo.

**Privacy (NFR4/NFR11):** persoonsgegevens en afspraakdetails (telefoonnummer/recipient, subject,
body) worden **nooit** gelogd. De echte gegevens herleid je via `notification.id` in de database.

### Datasources voor Foutopsporing

`grafana/provisioning/datasources/loki-tempo.yml` voegt twee datasources toe: **Loki**
(uid `loki-app`, met een afgeleid `trace_id`-veld dat naar Tempo linkt) en **Tempo** (uid
`tempo-app`). Het otel-lgtm-image draait Loki op poort 3100 en Tempo op 3200 binnen dezelfde
container. Komen de labels in jouw omgeving niet overeen (bijv. een andere `service_name`),
pas dan de queries in `grafana/dashboards/error-tracing.json` aan.

---

## Tijdbereik aanpassen

Rechtsboven in Grafana kun je het tijdbereik instellen. Voor realtime bewaking gebruik je `Last 5 minutes` met auto-refresh op `10s`.

---

## Technische achtergrond

Zie [ADR-5](../ADR/ADR-5.md) voor de onderbouwing van de keuze voor de OpenTelemetry en Grafana LGTM-stack.

De dashboarddefinitie staat in `grafana/dashboards/rabbitmq.json` en wordt elke 10 seconden automatisch herladen bij wijzigingen.
