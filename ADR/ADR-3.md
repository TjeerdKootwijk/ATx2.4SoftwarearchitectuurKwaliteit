---
status: Accepted
date: 2026-04-22
deciders: Groep C1
---

# AD: Integratiemethode tussen OpenMRS en de communicatiemodule

## Context and Problem Statement

De communicatiemodule moet afspraakdata ontvangen van OpenMRS om notificaties te kunnen versturen. De integratie moet meerdere OpenMRS-instanties ondersteunen, bestand zijn tegen downtime, en aansluiten op de HL7/FHIR-standaard. De module moet als standalone applicatie functioneren zonder aanpassingen aan bestaande OpenMRS-installaties. Er moet bepaald worden via welk mechanisme de module de benodigde data verkrijgt.

## Decision Drivers

* Geen aanpassingen aan bestaande OpenMRS-installaties (harde eis: standalone app)
* Ondersteuning voor meerdere OpenMRS-organisaties (multitenancy)
* Robuustheid bij netwerk of providerstoringen
* Naleving van HL7/FHIR-standaard
* Traceerbaarheid en logging

## Considered Options

1. **OpenMRS REST API (polling)**: De module haalt periodiek afspraken op via de OpenMRS REST API.
2. **OpenMRS Webhooks/Events**: OpenMRS stuurt bij afspraakwijzigingen een HTTP-request naar de module.
3. **FHIR-server als tussenpersoon**: OpenMRS synchroniseert naar een FHIR-server, de module leest van de FHIR-server.
4. **Message broker (zoals Kafka of RabbitMQ)**: OpenMRS publiceert afspraakevents naar een gedeelde broker.

## Decision Outcome

Gekozen optie: Optie 1 - OpenMRS REST API (polling elke minuut), omdat dit de enige optie is die geen aanpassingen aan OpenMRS vereist. Webhooks, een FHIR-tussenserver en een message broker vereisen allemaal extra modules of configuratie aan de OpenMRS-zijde, wat in strijd is met de eis dat de communicatiemodule een standalone applicatie moet zijn.

### Consequences

**Good:**
- Geen aanpassingen nodig aan bestaande OpenMRS-installaties.
- De module is volledig standalone en kan gekoppeld worden aan elke OpenMRS-instantie die de standaard REST API aanbiedt.
- Eenvoudige multitenancy: per tenant wordt een polling-configuratie bijgehouden.
- Voorspelbare belasting en eenvoudig te monitoren.

**Bad:**
- Notificaties hebben een vertraging tot maximaal het polling-interval (ca. 60 seconden).
- Continue belasting op OpenMRS, ook als er geen wijzigingen zijn.
- Schaalt minder goed bij grote aantallen tenants; polling-intervals moeten zorgvuldig afgestemd worden.

### Polling-strategie

De module poll elke minuut per tenant via de OpenMRS REST API (of FHIR REST endpoint indien beschikbaar). Om duplicaten en onnodige dataoverdracht te voorkomen:

- Gebruik van een `lastUpdated`-filter zodat alleen gewijzigde afspraken sinds de vorige poll worden opgehaald.
- Per tenant wordt een cursor/timestamp bijgehouden van de laatste succesvolle synchronisatie.
- Polling-interval is configureerbaar per tenant.
- Jitter wordt toegepast zodat polling-requests van verschillende tenants niet gelijktijdig plaatsvinden.
- Bij eerste koppeling wordt een initiële volledige synchronisatie uitgevoerd vanaf een configureerbare startdatum.

## More Information

* Voor FHIR-compliance kan OpenMRS een FHIR REST endpoint aanbieden (OpenMRS FHIR module), indien beschikbaar wordt deze gebruikt boven de standaard REST API.
* De module implementeert een Status behoudend verwerking om dubbele notificaties te voorkomen.
* Bij herhaalde fouten van een specifieke provider worden berichten in RabbitMQ in een dead letter queue geplaatst voor handmatige inspectie.
* De integratie wordt gedocumenteerd voor technisch beheerders, inclusief voorbeeldauthenticatie (API-keys, OAuth2 client credentials).