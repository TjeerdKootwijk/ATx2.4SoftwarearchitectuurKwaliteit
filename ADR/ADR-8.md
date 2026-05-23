---
status: Accepted
date: 2026-05-23
deciders: Groep C1
---

# ADR-8: String-gebaseerde provider-identificatie ter vervanging van de ProviderType-enum

## Context and Problem Statement

De communicatiemodule ondersteunt meerdere messaging providers (SwiftSend, LegacyLink, AsyncFlow, SecurePost). De opdrachtgever vereist dat nieuwe providers eenvoudig kunnen worden toegevoegd zonder grote aanpassingen aan de bestaande codebase. In de initiële implementatie werd een Java-enum gebruikt om providers te identificeren. Dit betekende dat bij iedere nieuwe provider de enum moest worden aangepast, samen met alle plaatsen in de code die daarvan afhankelijk waren. Dit schendt het Open/Closed Principe: de code moest worden gewijzigd in plaats van uitgebreid.

## Decision Drivers

* **OCP (Open/Closed Principle)**: het systeem moet open zijn voor uitbreiding maar gesloten voor modificatie van bestaande klassen.
* **Uitbreidbaarheid**: een nieuwe provider mag uitsluitend een nieuwe implementatie vereisen, zonder aanpassingen aan de bestaande code.
* **Eenvoud**: de oplossing moet begrijpelijk zijn voor nieuwe teamleden en toekomstige bijdragers.
* **NFR3**: de module dient toekomstbestendig uitbreidbaar te zijn met nieuwe communicatieproviders.

## Considered Options

De eerste optie was de enum behouden zoals hij was. Dit geeft compile-time type-veiligheid maar vereist bij elke nieuwe provider een aanpassing aan de enum én aan alle code die `ProviderType.valueOf()` gebruikt. Dat is een schending van OCP.

De tweede optie, die gekozen is, was de enum omzetten naar een klasse met `static final String`-constanten en de interface te laten werken met een gewone String als provider-naam. De factory bouwt automatisch een map op van alle Spring-beans die de interface implementeren. Hierdoor registreert een nieuwe provider zichzelf zodra Spring de klasse detecteert.

Een derde optie was een annotatie-gedreven aanpak waarbij elke provider een eigen `@ProviderName`-annotatie krijgt. Dit is flexibeler maar vereist extra infrastructuurcode en was buiten de scope van dit project.

## Decision Outcome

Gekozen optie: **String-gebaseerde provider-identificatie**.

De `ProviderType`-klasse bestaat nog steeds maar is geen enum meer. Hij bevat alleen constanten zoals `SWIFTSEND`, `SECUREPOST` enzovoort als benoemde Strings, zodat er geen losse tekstvariabelen in de code staan. De interface schrijft een methode `getProviderName()` voor die een String teruggeeft. De factory verzamelt bij het opstarten automatisch alle provider-implementaties die Spring vindt en slaat ze op met die naam als sleutel.

Een nieuwe provider toevoegen betekent nu uitsluitend een nieuwe klasse schrijven die de interface implementeert en de juiste naam teruggeeft. Er hoeft geen enkele bestaande klasse te worden gewijzigd.

### Consequences

**Good:**
- Volledige OCP-naleving: uitbreiding zonder modificatie van bestaande bestanden.
- De factory geeft bij een onbekende provider een foutmelding met de lijst van alle bekende providers, wat debuggen vereenvoudigt.
- De `ProviderType`-constanten blijven beschikbaar als benoemde waarden zodat magic strings vermeden worden.

**Bad:**
- Er is geen compile-time validatie meer op de provider-naam. Een typefout wordt pas bij het opstarten van de applicatie ontdekt via de foutmelding van de factory. Dit is acceptabel omdat de naam uit configuratie komt en de fout direct zichtbaar is.
