# Architectural Decision Records

Dit logboek bevat alle architectuurbeslissingen van de OpenMRS communicatiemodule. Elke ADR beschrijft het probleem, de overwogen opties en de reden voor de uiteindelijke keuze.

---

## Overzicht

| ADR | Titel | Status | Datum |
|---|---|---|---|
| [ADR-1](ADR-1.md) | Positionering als standalone SaaS service | Accepted | 2026-04-22 |
| [ADR-2](ADR-2.md) | Technologiestack (Java, Spring Boot, RabbitMQ, PostgreSQL, Grafana) | Accepted | 2026-04-22 |
| [ADR-3](ADR-3.md) | Integratiemethode via REST API polling | Accepted | 2026-04-22 |
| [ADR-4](ADR-4.md) | Asynchrone verwerking via RabbitMQ met idempotency | Accepted | 2026-05-06 |
| [ADR-5](ADR-5.md) | Observability via OpenTelemetry en de Grafana LGTM-stack | Accepted | 2026-05-21 |
| [ADR-6](ADR-6.md) | Immutable audit log per notificatiepoging | Accepted | 2026-05-23 |
| [ADR-7](ADR-7.md) | Intern gebruik van FHIR R4 als berichtenstandaard | Accepted | 2026-05-23 |
| [ADR-8](ADR-8.md) | String-gebaseerde provider-identificatie ter vervanging van de ProviderType-enum | Accepted | 2026-05-23 |
| [ADR-9](ADR-9.md) | UTF-8 als verplichte karakterset door de gehele verwerkingspipeline | Accepted | 2026-05-25 |
| [ADR-10](ADR-10.md) | Versiecompatibiliteit met OpenMRS 2.7.x+ en runtime-verificatie bij opstart | Accepted | 2026-05-25 |
| [ADR-11](ADR-11.md) | Per-tenant tijdzone-ondersteuning via IANA timezone ID | Accepted | 2026-05-25 |

---

## Samenvatting van de kernbeslissingen

**ADR-1: Standalone SaaS service**
De module draait als een zelfstandige applicatie los van OpenMRS. Meerdere OpenMRS-organisaties (tenants) kunnen de module gebruiken zonder hun eigen OpenMRS-installatie te wijzigen.

**ADR-2: Technologiestack**
Gekozen voor Java met Spring Boot, RabbitMQ als message broker, PostgreSQL als database en Prometheus met Grafana voor monitoring. Deze combinatie past bij de HL7/FHIR-eisen en de beveiligingsvereisten (AES-256, TLS 1.3).

**ADR-3: REST API polling als integratiemethode**
De module haalt afspraakdata op via de OpenMRS REST API. Dit is de enige methode waarvoor geen aanpassingen aan OpenMRS nodig zijn. Polling wordt elke minuut uitgevoerd per tenant, met een `lastUpdated` filter om alleen wijzigingen op te halen.

**ADR-4: Asynchrone verwerking via RabbitMQ**
De PollingJob publiceert afspraakevents naar een RabbitMQ queue. Een aparte consumer verwerkt de events en verstuurt notificaties. Idempotency voorkomt dat dezelfde afspraak twee keer wordt verwerkt. Bij fouten wordt exponentieel backoff toegepast; bij aanhoudende fouten gaat het event naar een dead-letter queue.

**ADR-5: Observability via LGTM-stack**
Alle drie de observability-pijlers zijn beschikbaar: metrics via Prometheus en Grafana, logs via Loki en traces via Tempo. De OpenTelemetry Java agent zorgt voor automatische instrumentatie.

**ADR-6: Immutable audit log**
Elke notificatiepoging schrijft een nieuw record in de `notification_logs` tabel. Bestaande records worden nooit overschreven. Zo is de volledige bezorggeschiedenis altijd reconstrueerbaar, ook na meerdere herproberingen.

**ADR-7: FHIR R4 als intern datamodel**
Hoewel OpenMRS geen native FHIR Appointments levert, worden de opgehaalde afspraken intern gemapt naar FHIR R4 objecten voor validatie via HAPI FHIR. Dit maakt de module toekomstbestendig voor bronnen die wel native FHIR ondersteunen.

**ADR-8: String-gebaseerde provider-identificatie**
De ProviderType-enum is vervangen door string-constanten. De ProviderFactory bouwt automatisch een overzicht op van alle geregistreerde providers. Een nieuwe provider toevoegen vereist alleen een nieuwe klasse, zonder aanpassingen aan bestaande code (Open/Closed Principle).

**ADR-9: UTF-8 als verplichte karakterset**
Alle byte/String-grenzen in de verwerkingspipeline gebruiken expliciet `StandardCharsets.UTF_8`. De JVM wordt opgestart met `-Dfile.encoding=UTF-8` zodat het gedrag omgevingsonafhankelijk is. Dit is vereist voor NFR8 (diverse karaktersets) en voorkomt datacorruptie bij AES-256-GCM versleuteling van niet-ASCII tekens.

**ADR-10: Versiecompatibiliteit met OpenMRS 2.7.x+**
De module vereist OpenMRS 2.7.x+ met de Appointments Module (`openmrs-module-appointments`). Bij tenant-registratie controleert `OpenMrsCompatibilityChecker` of het session-endpoint bereikbaar is en of de Appointments Module beschikbaar is. Ontbrekende endpoints worden gelogd als waarschuwingen; de module start altijd op (NFR7).

**ADR-11: Per-tenant tijdzone-ondersteuning**
Elke tenant heeft een IANA timezone ID (bijv. `"Europe/Amsterdam"`) opgeslagen in de database. De tijdzone stroomt als `ZoneId` door de FHIR-mapper en als IANA-string mee in het `AppointmentChangedEvent`. Zo worden de 24u- en 1u-notificatievensters berekend in de lokale tijd van de tenant, ongeacht de servertijdzone (NFR13).
