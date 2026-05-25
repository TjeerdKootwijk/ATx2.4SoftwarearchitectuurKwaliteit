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

<!-- Vervang de onderstaande afbeelding met een screenshot van het Grafana dashboard -->
![Grafana dashboard overzicht](../docs/grafana-dashboard.png)

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

<!-- Vervang de onderstaande afbeelding met een screenshot van een specifiek dashboard paneel -->
![Dashboard detailpaneel](../docs/grafana-detail.png)

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
