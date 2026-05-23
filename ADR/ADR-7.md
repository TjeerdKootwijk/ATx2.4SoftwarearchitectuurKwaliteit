---
status: Accepted
date: 2026-05-23
deciders: Groep C1
---

# AD: Intern gebruik van FHIR R4 als berichtenstandaard, ondanks ontbrekende FHIR-support in OpenMRS

## Context and Problem Statement

De communicatiemodule haalt afspraakdata op uit OpenMRS via de propriëtaire REST API (`/ws/rest/v1/appointment`). Deze API retourneert een eigen JSON-formaat — geen FHIR. OpenMRS heeft wel beperkte FHIR-ondersteuning via de FHIR2-module, maar de `Appointment`-resource is daar niet in opgenomen.

Toch verwerkt de module de opgehaalde afspraakdata intern via FHIR R4 `Appointment`-objecten, voordat ze worden omgezet naar interne events en op de queue worden gezet.

De vraag is: heeft het zin om FHIR R4 intern te gebruiken als de bron (OpenMRS) en de bestemming (notificatie via provider) beide geen FHIR spreken?

## Decision Drivers

* **Standaardisatie**: FHIR R4 is de internationale standaard voor medische data-uitwisseling; het gebruik ervan maakt de module toekomstbestendig.
* **Uitbreidbaarheid**: Als OpenMRS in de toekomst wel FHIR Appointments ondersteunt, of als een andere bron (Epic, Hl7, etc.) wordt gekoppeld, hoeft alleen de inkomende mapper te worden vervangen.
* **Validatie**: FHIR biedt een gestandaardiseerde validatielaag (verplichte velden, typed waarden) die voorkomt dat onvolledige afspraken worden verwerkt.
* **NFR6**: Het systeem moet berichttransformatie en validatie ondersteunen.

## Considered Options

1. **FHIR R4 als intern datamodel** — OpenMRS JSON wordt gemapt naar een FHIR R4 `Appointment`-object, gevalideerd via HAPI FHIR, en daarna omgezet naar een intern `AppointmentChangedEvent`.
2. **Directe mapping zonder tussenlaag** — OpenMRS JSON wordt direct omgezet naar een intern `AppointmentChangedEvent`, zonder FHIR tussenkomst.
3. **Eigen intern domeinmodel** — Een zelfgedefinieerd domeinmodel (geen FHIR) fungeert als tussenlaag tussen OpenMRS en het interne event.

## Decision Outcome

Gekozen optie: **Optie 1: FHIR R4 als intern datamodel**, omdat het een gestandaardiseerde validatielaag toevoegt en de module voorbereid op toekomstige FHIR-compatibele bronnen, zonder de bestaande flow te compliceren.

### Waarom FHIR ondanks het ontbreken van native support in OpenMRS?

OpenMRS levert geen FHIR Appointments, maar dat betekent niet dat FHIR intern geen waarde heeft. De mapper (`FhirR4AppointmentMapper`) zet het OpenMRS-formaat om naar een gestandaardiseerd FHIR-object. Dit geeft twee voordelen:

**Validatie via HAPI FHIR** — De `FhirAppointmentValidator` controleert of het gemaakte FHIR-object valide is (verplichte velden aanwezig, correct typed). Afspraken die de validatie niet doorstaan worden gelogd en overgeslagen, wat voorkomt dat onvolledige data in de notificatieflow terechtkomt.

**Uitwisselbaarheid van de bron** — Als in de toekomst een andere EPD-leverancier wordt gekoppeld die wel native FHIR levert (Epic, Azure Health Data Services, etc.), hoeft alleen de `AppointmentFetcher` en de mapper te worden vervangen. De rest van de pipeline — validatie, event-conversie, queueing — blijft ongewijzigd.

### Waarom niet direct mappen (optie 2)?

Directe mapping is eenvoudiger maar mist een gestandaardiseerde validatielaag. Elke toekomstige bronkoppeling zou opnieuw eigen validatielogica vereisen. Bovendien sluit het de module af van het groeiende FHIR-ecosysteem.

### Waarom geen eigen domeinmodel (optie 3)?

Een eigen model geeft volledige controle maar is niet gestandaardiseerd. Er is geen bestaande tooling voor validatie en er is geen gedeelde taal met andere systemen in de zorgketen.

### Consequences

**Good:**
- Gestandaardiseerde validatie via HAPI FHIR voorkomt verwerking van onvolledige afspraken.
- De bronkoppeling is uitwisselbaar zonder de rest van de pipeline te raken (OCP).
- De module is voorbereid op toekomstige native FHIR-bronnen.
- Compliant met NFR6 (berichttransformatie en validatie).

**Bad:**
- FHIR R4 voegt een extra transformatiestap toe die technisch niet strikt noodzakelijk is voor de huidige OpenMRS-koppeling.
- HAPI FHIR is een zware dependency voor een validatielaag die intern blijft.
- Ontwikkelaars moeten bekend zijn met FHIR R4-terminologie om de mapper te onderhouden.

## More Information

- Relevante klassen: `FhirR4AppointmentMapper`, `FhirAppointmentValidator`, `AppointmentEventConverter`, `OpenMrsRestAppointmentFetcher`.
- HAPI FHIR R4 dependency: `ca.uhn.hapi.fhir:hapi-fhir-structures-r4`.
- Zie ADR-1 (standalone service) voor de bredere architectuurkeuze en ADR-2 (technologiestack).
