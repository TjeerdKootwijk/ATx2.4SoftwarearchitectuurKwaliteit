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

## Tijdbereik aanpassen

Rechtsboven in Grafana kun je het tijdbereik instellen. Voor realtime bewaking gebruik je `Last 5 minutes` met auto-refresh op `10s`.

---

## Technische achtergrond

Zie [ADR-5](../ADR/ADR-5.md) voor de onderbouwing van de keuze voor de OpenTelemetry en Grafana LGTM-stack.

De dashboarddefinitie staat in `grafana/dashboards/rabbitmq.json` en wordt elke 10 seconden automatisch herladen bij wijzigingen.
