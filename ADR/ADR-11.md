---
status: Accepted
date: 2026-05-25
deciders: Groep C1
---

# ADR-11: Per-tenant tijdzone-ondersteuning via IANA timezone ID

## Context and Problem Statement

De communicatiemodule verstuurt herinneringen 24 uur en 1 uur voor een afspraak. Om die vensters correct te berekenen moet de module weten in welke lokale tijd een afspraak gepland staat. NFR13 stelt dat de module tijdzone-bewust moet zijn.

De oorspronkelijke implementatie gebruikte overal `ZoneId.systemDefault()`. Dit is onjuist voor een SaaS-module die meerdere tenants bedient, omdat de systeemtijdzone van de server verschilt van de tijdzone van de patiënt. Een ziekenhuis in Amsterdam dat draait op een UTC-server zou daardoor notificaties op de verkeerde tijden ontvangen.

## Decision Drivers

* **NFR13**: Tijdzone-verwerking moet correct zijn en per tenant configureerbaar.
* **NFR1**: De module ondersteunt meerdere onafhankelijke OpenMRS-instanties. Tenants kunnen in verschillende tijdzones zitten.
* **Correctheid**: De 24u- en 1u-vensters moeten berekend worden in de lokale tijd van de tenant, niet de servertijd.

## Considered Options

1. **Per-tenant IANA timezone ID opgeslagen in de database (gekozen)**: Elke tenant heeft een `timezone`-kolom (bijv. `"Europe/Amsterdam"`). De tijdzone wordt doorgegeven door de hele pipeline: van polling tot eventconversie tot notificatieberekening.
2. **Vaste UTC-aanname**: Alle tijden worden als UTC beschouwd en er is geen tijdzone-configuratie per tenant.
3. **Tijdzone afleiden uit de OpenMRS-instantie**: Bij elke poll worden de serverinstellingen van OpenMRS opgevraagd om de tijdzone te bepalen.

## Decision Outcome

Gekozen optie: **Optie 1, per-tenant IANA timezone ID in de database**.

Optie 2 is functioneel onjuist voor tenants buiten UTC. Optie 3 introduceert een extra API-afhankelijkheid per poll-cyclus en is gevoelig voor misconfiguratie aan de OpenMRS-kant.

### Hoe werkt het?

De tijdzone wordt als IANA ID (bijv. `"Europe/Amsterdam"` of `"America/New_York"`) geconfigureerd via de omgevingsvariabele `OPENMRS_TIMEZONE` en opgeslagen in de `tenants`-tabel.

De tijdzone stroomt door de pipeline in twee vormen:

1. **Als `ZoneId`** in `PollingJob` en `FhirR4AppointmentMapper`: de UTC-epoch van OpenMRS wordt omgezet naar `LocalDateTime` in de tijdzone van de tenant.
2. **Als `String` (IANA ID)** in het `AppointmentChangedEvent`: zodat `AppointmentService` de juiste `ZoneId` kan reconstrueren bij het berekenen van de notificatievensters.

```
OpenMRS epoch (UTC)
    └─→ FhirR4AppointmentMapper.convert(..., tenantZone)
           └─→ AppointmentChangedEvent.appointmentDateTime  (LocalDateTime in tenant TZ)
           └─→ AppointmentChangedEvent.timezone             (IANA ID, bijv. "Europe/Amsterdam")
                   └─→ AppointmentService: LocalDateTime.atZone(ZoneId.of(event.getTimezone()))
```

Als de geconfigureerde tijdzone-ID ongeldig is, valt `PollingJob.resolveZone()` terug op UTC en wordt een waarschuwing gelogd. De module crasht hierdoor niet.

### Keuze voor `LocalDateTime` in plaats van `ZonedDateTime` of `Instant`

Het veld `AppointmentChangedEvent.appointmentDateTime` is van het type `LocalDateTime`, zonder tijdzone-informatie ingebakken. De tijdzone wordt apart meegestuurd als IANA-ID. Dit is een bewuste keuze om de volgende redenen:

- Het event is een intern overdrachtsmodel. De ontvanger (`AppointmentService`) kent de tijdzone via `event.getTimezone()` en kan zelf de conversie naar `Instant` doen.
- `ZonedDateTime` als veldtype zou de serialisatie via RabbitMQ/Jackson compliceren zonder aanvullende configuratie.
- `Instant` zou de lokale tijdzone-context verliezen die nodig is voor de vensterberekening.

### Gevolgen voor de database

Flyway-migratie `V2__add_tenant_timezone.sql` voegt de kolom toe:

```sql
ALTER TABLE tenants ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
```

Bestaande tenants krijgen automatisch `UTC` als tijdzone, wat achterwaarts compatibel is.

### Consequences

**Good:**
- Notificaties worden verstuurd op het juiste lokale moment voor elke tenant, ongeacht de servertijdzone.
- De tijdzone is expliciet en terug te vinden in logs, events en de database.
- Bij een ongeldige configuratie schakelt de module terug naar UTC in plaats van te crashen.

**Bad:**
- Een tenant heeft precies een tijdzone. Ziekenhuizen met locaties in meerdere tijdzones moeten per locatie een aparte tenant registreren.
- De combinatie van `LocalDateTime` en een los IANA-ID vereist discipline: de twee waarden moeten altijd bij elkaar passen.

## More Information

- Zie ADR-1 (multi-tenancy) voor de bredere context van per-tenant configuratie.
- Zie ADR-3 (polling) voor de pipeline waarin de tijdzone-conversie plaatsvindt.
- IANA Time Zone Database: https://www.iana.org/time-zones
