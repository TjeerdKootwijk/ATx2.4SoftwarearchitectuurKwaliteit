---
status: Accepted
date: 2026-04-22
deciders: Groep C1
---

# AD: Positionering van de communicatiemodule: standalone service of ingebouwde OpenMRS-module

## Context and Problem Statement

De communicatiemodule moet notificaties kunnen versturen voor OpenMRS-organisaties wereldwijd. De opdrachtgever wil de module als zelfstandig product aanbieden, bij voorkeur als SaaS. Er zijn twee fundamenteel verschillende architecturen mogelijk: de module bouwen als een standalone service (los van OpenMRS) of als een ingebouwde module binnen OpenMRS (bijvoorbeeld als OpenMRS Module of OWA). Deze keuze bepaalt de haalbaarheid van multitenancy, onderhoudbaarheid, en schaalbaarheid.

## Decision Drivers

* **Multitenancy**: Meerdere OpenMRS-organisaties moeten de module kunnen gebruiken, elk met eigen providers en credentials.
* **Onafhankelijke updates**: De module moet kunnen worden bijgewerkt worden zonder een OpenMRS-upgrade af te dwingen.
* **Schaalbaarheid**: De module moet pieken in berichtenverkeer kunnen opvangen zonder OpenMRS te beïnvloeden.
* **Beveiliging**: Gevoelige credentials (API-keys voor SwiftSend, LegacyLink, AsyncFlow, SecurePost) moeten per organisatie veilig worden opgeslagen en gescheiden.
* **Uitbreidbaarheid**: Nieuwe communicatieproviders moeten kunnen worden toegevoegd zonder OpenMRS aan te passen.
* **Integratie met andere OpenMRS-modules**: De module moet in de toekomst ook medische testresultaten kunnen versturen (zonder grote wijzigingen).

## Considered Options

1. **Standalone service (SaaS)** De module draait als een aparte applicatie, volledig onafhankelijk van OpenMRS. Communicatie verloopt via een netwerkprotocol (REST API, webhooks). Elke OpenMRS-organisatie krijgt een eigen tenant binnen de module.
2. **Ingebouwde OpenMRS-module** De module wordt gebouwd als een OpenMRS-module (OWA of backend-module) en draait binnen het OpenMRS-proces. De module deelt de database en infrastructuur van OpenMRS.
3. **Hybride model** Een OpenMRS-module fungeert als lichtgewicht gateway die berichten doorstuurt naar een externe berichtenwachtrij, waar een aparte worker de daadwerkelijke verzending doet.

## Decision Outcome

Chosen option: **Optie 1 - Standalone service (SaaS-oplossing)**, omdat deze het beste past bij de eis dat de module door meerdere onafhankelijke OpenMRS-organisaties gebruikt kan worden zonder dat zij hun OpenMRS-infrastructuur hoeven aan te passen.

### Waarom niet ingebouwd?

Een ingebouwde OpenMRS-module (optie 2) zou betekenen dat:
- Elke OpenMRS-organisatie de module handmatig moet installeren en bijwerken.
- De module gebonden is aan de OpenMRS-versie en de daarin beschikbare technologiestack (oudere Java-versies, beperkte queueing-opties).
- Credentials van verschillende organisaties in één OpenMRS-database terechtkomen, wat beveiligingsrisico's met zich meebrengt.
- Schaalbaarheid beperkt is tot de schaal van OpenMRS zelf.

Optie 3 (hybride) voegt complexiteit toe zonder de voordelen van volledige onafhankelijkheid.

### Consequences

**Good:**
- De module kan meerdere OpenMRS-instanties tegelijk bedienen (multitenancy).
- Gevoelige authenticatiegegevens per organisatie kunnen veilig worden gescheiden.
- Updates en uitbreidingen (nieuwe providers) kunnen onafhankelijk van OpenMRS worden uitgerold.
- De module kan worden geschaald op basis van berichtvolume zonder OpenMRS te raken.
- De module kan worden aangeboden als SaaS, wat past bij de wens van de opdrachtgever.

**Bad:**
- Er is een extra netwerklaag tussen OpenMRS en de module (latentie, extra foutgevoeligheid).
- De module moet eigen authenticatie en tenantmanagement implementeren.
- OpenMRS-beheerders moeten de netwerkkoppeling configureren (firewall, API-keys).

## More Information

- Deze keuze sluit een gedeelde message broker tussen OpenMRS en de module uit (past niet bij multitenancy).
- De module wordt als Docker-container geleverd met duidelijke documentatie voor OpenMRS-beheerders over hoe de koppeling te configureren.
- Een follow-up ADR zal de exacte tenant-identificatie en authenticatiemethode (API-keys, JWT, OAuth2) vastleggen.