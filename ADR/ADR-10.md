---
status: Accepted
date: 2026-05-25
deciders: Groep C1
---

# ADR-10: Versiecompatibiliteit met OpenMRS 2.7.x+ en runtime-verificatie bij opstart

## Context and Problem Statement

De communicatiemodule haalt afspraakdata op via de OpenMRS REST API. NFR4 stelt dat de module moet werken met OpenMRS vanaf versie 2.7.x. De codebase maakte nergens expliciet duidelijk:

- welke specifieke OpenMRS-versie en module vereist zijn;
- hoe de module reageert als het vereiste endpoint niet beschikbaar is;
- op welk moment de beheerder een waarschuwing krijgt als de koppeling niet klopt.

## Decision Drivers

* **NFR4**: De communicatiemodule dient te worden gekoppeld aan OpenMRS 2.7.x+.
* **NFR7**: De module moet zelfstandig kunnen functioneren bij tijdelijke onbeschikbaarheid van OpenMRS, een ontbrekend endpoint mag de module niet laten crashen.
* **Beheerbaarheid**: Beheerders moeten bij het opstarten direct zien of de koppeling correct is geconfigureerd.

## Gebruikte endpoints

De module gebruikt uitsluitend de OpenMRS REST API v1:

| `GET /ws/rest/v1/session` | Authenticatieverificatie (Basic Auth) | OpenMRS 2.x core |
| `GET /ws/rest/v1/appointments?v=full` | Ophalen van afspraken voor polling | OpenMRS 2.7.x + Appointments Module |

Het `/ws/rest/v1/appointments` endpoint is onderdeel van de **OpenMRS Appointments Module** (`openmrs-module-appointments`). Dit is een aparte module die naast de OpenMRS 2.7.x core geïnstalleerd moet zijn. De module is niet onderdeel van de OpenMRS-standaardinstallatie.

Authenticatie verloopt via HTTP Basic Auth (RFC 7617). Per poll-cyclus worden alle actieve afspraken opgehaald met de query-parameter `v=full` voor een volledige representatie.

## Considered Options

1. **Runtime-verificatie bij opstart (gekozen)**: Bij het registreren van een tenant controleert een `OpenMrsCompatibilityChecker` of het session-endpoint bereikbaar is en of de Appointments Module beschikbaar is. Fouten worden gelogd als waarschuwingen; de module blijft opstarten.
2. **Alleen documentatie, geen code**: De versievereiste wordt uitsluitend in documentatie vastgelegd, zonder runtime-controle.
3. **Harde fout bij ontbrekend endpoint**: De module weigert op te starten als het Appointments endpoint niet bereikbaar is.

## Decision Outcome

Gekozen optie: **Optie 1: runtime-verificatie bij opstart**.

Optie 2 geeft beheerders geen directe feedback bij een misconfiguratie. Optie 3 conflicteert met NFR7: als OpenMRS tijdelijk onbereikbaar is bij het opstarten van de communicatiemodule, mag de module niet weigeren te starten.

### Hoe werkt de verificatie?

Bij elke tenant-registratie (via `TenantInitializer.registerTenants()`) roept de `OpenMrsCompatibilityChecker` twee checks uit:

1. **Sessie-check**: `GET /ws/rest/v1/session` met de geconfigureerde credentials. Controleert of OpenMRS bereikbaar is en de credentials geldig zijn. Bij een 401 wordt een waarschuwing gelogd met de instructie om `OPENMRS_USERNAME` en `OPENMRS_PASSWORD` te controleren.

2. **Appointments-check**: `GET /ws/rest/v1/appointments?limit=1`. Controleert of de Appointments Module geïnstalleerd is. Bij een 404 wordt een waarschuwing gelogd met de instructie om de `openmrs-module-appointments` module te installeren.

Beide checks loggen op `WARN`-niveau bij afwijkingen en gooien nooit een exception, zodat de module altijd opstart.

### Vereisten voor beheerders

Om de communicatiemodule te koppelen aan OpenMRS moet de beheerder zorgen voor:

- OpenMRS versie **2.7.x of hoger**
- De **OpenMRS Appointments Module** (`openmrs-module-appointments`) geïnstalleerd en actief
- Een OpenMRS-gebruiker met leesrechten op het `/ws/rest/v1/appointments` endpoint

### Consequences

**Good:**
- Beheerders zien bij opstart direct of de koppeling correct werkt (logregels met `NFR4` prefix zijn direct identificeerbaar).
- De module start altijd op, ook als OpenMRS tijdelijk onbereikbaar is (NFR7).
- De verificatie gebruikt dezelfde `RestTemplate` en auth-logica als de normale polling, waardoor ze consistent zijn.

**Bad:**
- De check geeft geen garantie dat de OpenMRS-versie exact 2.7.x is alleen dat het endpoint beschikbaar is. Een exacte versiecheck zou een extra call naar `/ws/rest/v1/systemsetting/core.config.versionNumber` vereisen, maar dat endpoint is niet in alle configuraties toegankelijk.
- De check wordt alleen bij opstart uitgevoerd. Als de Appointments Module later wordt uitgeschakeld, merkt de module dit pas via de polling-foutmeldingen.

## More Information

- Zie ADR-3 (integratiemethode via REST API polling) voor de keuze van polling boven webhooks.
- De `OpenMrsCompatibilityChecker` bevindt zich in het `fhir`-pakket omdat het tot de OpenMRS-integratiegrens behoort.
- OpenMRS Appointments Module: https://wiki.openmrs.org/display/docs/Appointments+Module
