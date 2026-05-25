# OpenMRS Communicatiemodule

Een SaaS communicatiemodule die afspraaknotificaties verstuurt namens OpenMRS-organisaties via externe messaging providers (SwiftSend, LegacyLink, AsyncFlow, SecurePost).

---

## Hoe het werkt

De module pollt periodiek de OpenMRS REST API per tenant, verwerkt afspraakwijzigingen asynchroon via RabbitMQ, en verstuurt notificaties 24 uur en 1 uur voor een afspraak via de geconfigureerde messaging provider.

<!-- Vervang de onderstaande afbeelding met jouw flow diagram screenshot -->
![Flow diagram](docs/flow-diagram.png)

---

## Snel opstarten

Vereisten: Docker en Docker Compose.

**Stap 1: Maak het configuratiebestand aan**

Kopieer het voorbeeldbestand en vul de lege waarden in:

```bash
cp .env.example .env
```

Genereer een AES-256 sleutel en zet die in `.env`:

```bash
openssl rand -base64 32
```

Vul daarna minimaal deze waarden in `.env` in:

```
AES_ENCRYPTION_KEY=<gegenereerde sleutel>
OPENMRS_BASE_URL=http://host.docker.internal:80/openmrs
OPENMRS_PASSWORD=<OpenMRS wachtwoord>
OPENMRS_NOTIFICATION_PROVIDER=SWIFTSEND
```

Zie [.env.example](.env.example) voor alle beschikbare opties met uitleg.

**Stap 2: Start alle services**

```bash
docker-compose up --build
```

Dit start: de Spring Boot applicatie, PostgreSQL, RabbitMQ, een nep-OpenMRS server, nep-messaging providers, Prometheus en Grafana.

**Stap 3: Controleer of alles draait**

```bash
docker-compose ps
```

Verwachte output: alle containers hebben status `running` of `healthy`.

**Stap 4: Test de module**

Maak een patient en afspraak aan in OpenMRS via het testscript:

```bash
python Scripts/create_patient_and_appointment.py
```

Het script maakt automatisch een patient aan en plant een afspraak in. De communicatiemodule pikt de afspraak op via polling en verstuurt notificaties op het juiste moment. Zie [Scripts/README.md](Scripts/README.md) voor de configuratieopties.

Controleer daarna in Grafana (http://localhost:3000) of de notificaties worden verwerkt.

---

## De module in werking

Dit laat zien hoe de module reageert wanneer hij draait en afspraken verwerkt.

**1. Health check**

```bash
curl http://localhost:8080/actuator/health
```

<!-- Voeg hier een screenshot toe van de health check response in de browser of terminal -->
![Health check response](docs/screenshot-health.png)

**2. Afspraak aanmaken via het testscript**

```bash
python Scripts/create_patient_and_appointment.py
```

<!-- Voeg hier een screenshot toe van de terminal output van het script (patient_uuid + appointment_uuid) -->
![Script output](docs/screenshot-script-output.png)

**3. Communicatiemodule pikt de afspraak op (applicatielogs)**

```bash
docker-compose logs -f app
```

<!-- Voeg hier een screenshot toe van de logs waaruit blijkt dat de polling job de afspraak heeft opgepikt en een notificatie heeft ingepland -->
![Applicatielogs](docs/screenshot-logs.png)

**4. Notificatie zichtbaar in Grafana**

<!-- Voeg hier een screenshot toe van het Grafana dashboard waarop de verzonden notificatie zichtbaar is -->
![Grafana notificatie](docs/screenshot-grafana.png)

---

## Monitoring

| Service | URL | Inloggegevens |
|---|---|---|
| Grafana dashboard | http://localhost:3000 | admin / admin |
| RabbitMQ beheer | http://localhost:15672 | guest / guest |
| Applicatie health | http://localhost:8080/actuator/health | - |
| Prometheus | http://localhost:9090 | - |

Zie [grafana/README.md](grafana/README.md) voor uitleg over het dashboard.

---

## Applicatiearchitectuur

<!-- Vervang de onderstaande afbeelding met jouw C4 architectuurdiagram -->
![Architectuurdiagram](docs/architecture.png)

De module bestaat uit de volgende onderdelen:

| Onderdeel | Functie |
|---|---|
| `PollingJob` | Pollt de OpenMRS REST API elke minuut per tenant |
| `IdempotencyService` | Voorkomt dat hetzelfde event twee keer wordt verwerkt |
| `NotificationConsumer` | Leest van de RabbitMQ queue en plant notificaties in |
| `NotificationScheduler` | Verstuurt notificaties op het juiste moment via de provider |
| `ProviderFactory` | Selecteert de juiste messaging provider per tenant |

```
OpenMRS (per tenant)
       |
       | REST API polling (elke minuut)
       v
  PollingJob
       |
       v
  RabbitMQ queue
       |
       v
NotificationConsumer
       |
       v
NotificationScheduler --> Messaging Provider (SwiftSend / LegacyLink / AsyncFlow / SecurePost)
```

---

## Architectuurbeslissingen

Alle beslissingen zijn vastgelegd als Architectural Decision Records. Zie [ADR/README.md](ADR/README.md) voor een overzicht.

---

## Beheerdersdocumentatie

De documentatie voor technisch OpenMRS-beheerders (koppeling, authenticatie en configuratie) staat in de beheerders handleiding die apart is aangeleverd.

---

## Beveiliging

- Gevoelige gegevens (credentials, tokens) worden versleuteld opgeslagen met AES-256-GCM. De GCM-modus biedt naast versleuteling ook tamper-detectie: als een waarde in de database is aangepast, weigert de applicatie het te verwerken.
- Binnenkomende afspraakdata wordt gevalideerd via HAPI FHIR R4 voordat die de verwerkingspipeline ingaat. Afspraken met ontbrekende verplichte velden (id, status, starttijd, participant) worden geweigerd en gelogd.
- Transport verloopt via TLS 1.3.
- Persoonsgegevens en afspraakdetails worden nooit in logbestanden opgeslagen.
- Patiëntdata wordt automatisch verwijderd na 14 dagen.
- Meta-informatie van verstuurde berichten wordt maximaal 1 jaar bewaard.

---

## Nieuwe messaging provider toevoegen

Maak een nieuwe klasse die de `MessagingProvider` interface implementeert en geef de juiste naam terug via `getProviderName()`. De `ProviderFactory` registreert de nieuwe provider automatisch bij het opstarten. Zie [ADR-8](ADR/ADR-8.md) voor de onderbouwing.
