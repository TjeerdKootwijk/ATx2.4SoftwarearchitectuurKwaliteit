---
status: Accepted
date: 2026-05-25
deciders: Groep C1
---

# ADR-9: UTF-8 als verplichte karakterset door de gehele verwerkingspipeline

## Context and Problem Statement

De communicatiemodule verwerkt afspraakmeldingen voor OpenMRS-organisaties wereldwijd (NFR8). Patiëntnamen, locatieomschrijvingen en instructies kunnen tekens bevatten uit uiteenlopende schriften: Arabisch, Chinees, Cyrillisch, of tekens met diakritische markeringen (é, ü, ç). Als de module deze tekens niet consistent verwerkt, kunnen berichten corrupt raken — met name bij versleuteling (AES-256-GCM) en bij HTTP-authenticatie.

Drie plaatsen in de code gebruikten `String.getBytes()` respectievelijk `new String(byte[])` zonder expliciete karakterset, waardoor het gedrag afhankelijk was van de standaardinstelling van de JVM. Op Alpine Linux — de basis van de Docker-container — is die standaard niet altijd gegarandeerd UTF-8, afhankelijk van de locale van het besturingssysteem.

## Decision Drivers

* **NFR8**: De module moet berichten kunnen verwerken in diverse karaktersets.
* **NFR5**: Gevoelige gegevens worden versleuteld met AES-256. Inconsistente encoding tussen versleutelen en ontsleutelen leidt tot onherstelbare datacorruptie.
* **Reproduceerbaarheid**: De module moet identiek gedragen op elke omgeving (ontwikkelaar, CI, productie) ongeacht de systeemlocale.

## Considered Options

1. **Expliciete `StandardCharsets.UTF_8` op alle byte/String-grenzen** — alle `getBytes()` en `new String(bytes)` aanroepen voorzien van een expliciete charset; JVM opgestart met `-Dfile.encoding=UTF-8`.
2. **Vertrouwen op Java 18+ standaard** — Sinds JEP 400 (Java 18) is UTF-8 de standaard JVM-charset. Omdat het project Java 21 gebruikt, zou dit in de praktijk werken.
3. **ISO-8859-1 als interne representatie** — Alleen Latijnse tekens ondersteunen voor providers die geen Unicode accepteren.

## Decision Outcome

Gekozen optie: **Optie 1: expliciete `StandardCharsets.UTF_8` op alle byte/String-grenzen**.

Optie 2 is technisch voldoende voor Java 21 maar mist documentaire waarde: de intentie is niet leesbaar uit de code en een toekomstige downgrade naar een oudere JVM zou stilzwijgend incorrect gedrag introduceren. Optie 3 voldoet niet aan NFR8.

### Consequences

**Good:**
- AES-256-GCM versleuteling en ontsleuteling produceren identieke bytes voor alle Unicode-tekens op alle omgevingen.
- Basic Auth-headers zijn RFC 7617-compliant voor niet-ASCII-gebruikersnamen en -wachtwoorden.
- De JVM-vlaggen in de Dockerfile maken de encoding-intentie zichtbaar en omgevingsonafhankelijk.

**Bad:**
- Op Java 21 zijn de JVM-vlaggen technisch redundant. Dit is bewuste explicitheid ten koste van minimale woordrijkheid.

## More Information

- Zie NFR8 (karaktersetondersteuning) en NFR5 (AES-256-GCM encryptie).
- RFC 7617 specificeert dat Basic Auth-credentials worden geëncodeerd als UTF-8 vóór Base64-codering.
- JEP 400 (UTF-8 by Default): https://openjdk.org/jeps/400
