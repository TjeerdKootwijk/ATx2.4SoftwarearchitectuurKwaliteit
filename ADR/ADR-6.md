---
status: Accepted
date: 2026-05-23
deciders: Groep C1
---

# AD: Immutable audit log — nieuw record per notificatiepoging in plaats van update

## Context and Problem Statement

De communicatiemodule verstuurt notificaties via externe providers (SwiftSend, LegacyLink, AsyncFlow, SecurePost). Providers kunnen tijdelijk onbeschikbaar zijn (503), waarna de RetryHandler de bezorging opnieuw probeert. Bij elke poging wordt het resultaat opgeslagen in de `notification_logs` tabel (FR2: auditbaarheid van verzonden notificaties).

De vraag is: bij een herpoging wordt het bestaande FAILED-record bijgewerkt naar SUCCESS, of wordt er een nieuw record aangemaakt?

## Decision Drivers

* **FR2**: Alle verzonden notificaties moeten auditeerbaar zijn inclusief mislukte pogingen.
* **Traceerbaarheid**: Beheerders en klanten moeten kunnen aantonen wanneer een notificatie is verstuurd en hoeveel pogingen daarvoor nodig waren.
* **Zorgomgeving**: In een medische context kan een patient of instelling achteraf vragen wanneer en hoe een bericht is bezorgd.
* **Eenvoud van implementatie**: De `RabbitMQConsumer` kent alleen het resultaat van de huidige poging, niet de volledige voorgeschiedenis.

## Considered Options

1. **Nieuw record per poging (immutable log)**: elke bezorgpoging schrijft een nieuw record in `notification_logs` met de actuele `retry_count` en het resultaat van die specifieke poging.
2. **Bestaand record bijwerken**: bij een retry wordt het bestaande FAILED-record overschreven met het nieuwe resultaat en de bijgewerkte `retry_count`.
3. **Hybride: één hoofd-record + detail-records**: één record per notificatie met de einststatus, aangevuld met een aparte tabel voor pogingshistorie.

## Decision Outcome

Gekozen optie: **Optie 1: nieuw record per poging (immutable log)**, omdat dit de volledige bezorggeschiedenis bewaart zonder complexe update-logica en het beste aansluit op de auditvereiste (FR2).

### Waarom geen update?

Een update (optie 2) overschrijft de mislukkinformatie. Na een succesvolle retry is niet meer zichtbaar dat er eerder een fout was, wanneer die fout optrad, en wat de foutmelding was. In een zorgomgeving is dit onacceptabel: als een patiënt nooit een bericht heeft ontvangen, moet de volledige bezorggeschiedenis reconstrueerbaar zijn.

Optie 3 (hybride) geeft de meeste informatie maar vereist een extra tabel, extra queries en extra logica in de consumer, voor een voordeel dat optie 1 al biedt.

### Hoe het werkt in de code

Bij elke poging in `RabbitMQConsumer` wordt één van de volgende methoden aangeroepen op `DataService`:

- `logNotificationSent(notificationId, tenantId, provider, providerMessageId, retryCount)` → status `SUCCESS`
- `logNotificationFailed(notificationId, tenantId, provider, errorMessage, retryCount)` → status `FAILED`

De `retryCount` geeft aan hoeveel eerdere pogingen al zijn gedaan. Zo is via de `notification_id` de volledige keten terug te vinden:

```
id=1 | notification_id=abc | FAILED  | retry_count=0 | error='Service unavailable'
id=4 | notification_id=abc | SUCCESS | retry_count=1 | provider_message_id='msg-xyz'
```

### Consequences

**Good:**
- Volledige bezorggeschiedenis is altijd reconstrueerbaar per `notification_id`.
- Geen update-logica nodig in de consumer, alleen schrijven.
- Compliant met FR2: ook mislukte pogingen zijn auditeerbaar.
- Records zijn immutable: geen risico op onbedoeld overschrijven van historische data.

**Bad:**
- De tabel groeit sneller dan bij een update-aanpak (één extra rij per mislukte poging).
- Een query naar "is deze notificatie uiteindelijk bezorgd?" vereist een filter op `notification_id` + `status = 'SUCCESS'` in plaats van een directe lookup.
- De nachtelijke cleanup (NFR11: maximaal 1 jaar bewaren) verwijdert ook de pogingshistorie.

## More Information

- Zie ADR-4 (retry- en dead-letter-beleid) voor de retrylogica en maximale pogingsaantallen.
- De `notification_logs` tabel bevat geen patiëntdata (NFR11); alleen meta-informatie over de bezorging.
- Cleanup via `DataService.cleanupExpiredData()` (dagelijks 02:00, records ouder dan 1 jaar).
