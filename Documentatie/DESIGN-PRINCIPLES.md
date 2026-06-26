# Ontwerpprincipes

Dit document beschrijft de ontwerpprincipes die expliciet zijn toegepast in de architectuur van het notificatiesysteem. Per principe wordt de locatie in de code aangewezen en de bijbehorende test die het bewijs levert.

---

## 1. Open/Closed Principle (OCP)

**Definitie:** Klassen staan open voor uitbreiding maar gesloten voor wijziging.

**Waar in de code:**
- Interface: [`MessagingProvider.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/provider/MessagingProvider.java)
- Factory: [`MessagingProviderFactory.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/provider/MessagingProviderFactory.java)
- Implementaties: `SwiftSendProvider`, `LegacyLinkProvider`, `AsyncFlowProvider`, `SecurePostProvider`

**Hoe het werkt:**

Een nieuwe messaging provider toevoegen vereist uitsluitend een nieuwe klasse die `MessagingProvider` implementeert en is voorzien van `@Service`. De `MessagingProviderFactory` registreert alle providers automatisch via Spring dependency injection, er hoeft **niets** in bestaande code gewijzigd te worden.

```java
// MessagingProvider.java  de interface die alle providers implementeren
public interface MessagingProvider {
    String getProviderName();
    ProviderSendResult sendMessage(NotificationQueueMessage message);
}

// Voorbeeld: SwiftSendProvider.java
@Service
public class SwiftSendProvider implements MessagingProvider {
    @Override
    public String getProviderName() { return ProviderType.SWIFTSEND; }

    @Override
    public ProviderSendResult sendMessage(NotificationQueueMessage message) { ... }
}
```

**Bewijs via ADR:** [ADR-8](ADR/ADR-8.md)  keuze voor de provider-factory met plugin-achtige uitbreidbaarheid.

**Bewijs via test:** `ArchitectureComplianceTest#alle_concrete_providers_implementeren_messaging_provider_interface` ArchUnit verifieert bij elke build dat alle `*Provider`-klassen de interface implementeren.

---

## 2. Strategy Pattern

**Definitie:** Een familie van algoritmen (providers) wordt ingekapseld achter één interface, zodat de keuze van algoritme op runtime kan worden gemaakt zonder de client te wijzigen.

**Waar in de code:**
- Context: [`RabbitMQConsumer.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/messaging/queue/RabbitMQConsumer.java)
- Strategy-interface: [`MessagingProvider.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/provider/MessagingProvider.java)
- Selectiemechanisme: [`MessagingProviderFactory.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/provider/MessagingProviderFactory.java)

**Hoe het werkt:**

Elke tenant heeft een eigen `notificationProvider`-configuratie (bijv. `SWIFTSEND` of `LEGACYLINK`). Wanneer een bericht uit de RabbitMQ-wachtrij wordt verwerkt, selecteert de `MessagingProviderFactory` op basis van deze waarde de juiste provider. De consumer hoeft niet te weten welke provider wordt gebruikt.

```java
// MessagingProviderFactory selecteert de juiste strategie op runtime
MessagingProvider provider = providerFactory.getProvider(message.getProvider());
ProviderSendResult result = provider.sendMessage(message);
```

Elke concrete provider (SwiftSend: JSON/REST, LegacyLink: XML/SOAP, AsyncFlow: async JSON, SecurePost: OAuth2 + JSON) implementeert dezelfde `sendMessage()`-methode maar met een eigen protocol.

**Bewijs via ADR:** [ADR-8](ADR/ADR-8.md)

**Bewijs via test:** `SystemTest#httpEndpoint_meerdereProviderTypes_gevenAllemalAccepted` alle vier providers worden aangesproken via dezelfde interface.

---

## 3. Idempotency

**Definitie:** Een operatie die meerdere keren wordt uitgevoerd heeft hetzelfde effect als één keer uitvoeren. Dit voorkomt dubbele notificaties bij herstart of herverwerking.

**Waar in de code:**
- Service: [`IdempotencyService.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/service/IdempotencyService.java)
- DAO: [`ProcessedEventDAO.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/dao/ProcessedEventDAO.java) / [`ProcessedEventDAOImpl.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/dao/impl/ProcessedEventDAOImpl.java)
- Entity: [`ProcessedEventJpaRepository.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/dao/jpa/ProcessedEventJpaRepository.java)

**Hoe het werkt:**

Van elk binnenkomend afspraakveld wordt een SHA-256 hash berekend op basis van `(tenantId + appointmentUuid + changeType)`. Deze hash wordt opgeslagen in PostgreSQL. Bij een duplicaat (bijv. door herstart van de applicatie of dubbele polling) wordt het event geweigerd voordat het de wachtrij bereikt.

```
Event-ID = SHA-256(tenantId + appointmentUuid + changeType)
→ Bestaat al in processed_events? → weiger (duplicate)
→ Nieuw? → verwerk + sla hash op
```

**Waarom dit noodzakelijk is:**

De polling-job draait elke 60 seconden en haalt afspraken op via een tijdsvenster. Bij een herstart of overlapend tijdsvenster kunnen dezelfde afspraken tweemaal worden opgehaald. Zonder idempotency zou een patiënt twee keer een herinnering ontvangen.

**Bewijs via ADR:** [ADR-7](ADR/ADR-7.md)  retry-mechanisme vereist idempotente verwerking.

---

## 4. Single Responsibility Principle (SRP)

**Definitie:** Elke klasse heeft één reden om te veranderen één verantwoordelijkheid.

**Waar in de code (concrete voorbeelden):**

| Klasse | Verantwoordelijkheid | Wat het NIET doet |
|---|---|---|
| [`PollingJob.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/service/PollingJob.java) | Ophalen van afspraken uit OpenMRS via REST | Verwerken, filteren, of versturen van notificaties |
| [`IdempotencyService.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/service/IdempotencyService.java) | Detecteren van dubbele events via SHA-256 | Ophalen van afspraken of versturen van berichten |
| [`AesEncryptionConverter.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/config/AesEncryptionConverter.java) | Transparant versleutelen/ontsleutelen van JPA-kolomwaarden | Bepalen wélke velden versleuteld worden (dat doet `@Convert` op de entity) |
| [`FhirAppointmentValidator.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/fhir/FhirAppointmentValidator.java) | Valideren van FHIR R4 appointment-resources | Ophalen of transformeren van afspraken |
| [`FhirR4AppointmentMapper.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/fhir/FhirR4AppointmentMapper.java) | Transformeren van FHIR-format naar intern model | Valideren of versturen |
| [`RabbitMQConsumer.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/messaging/queue/RabbitMQConsumer.java) | Ontvangen van berichten uit de wachtrij en routeren naar provider | Bepalen van provider-logica of opslaan van logs |
| [`DataService.java`](src/main/java/com/example/atx24softwarearchitectuurkwaliteit/service/DataService.java) | Opslaan van notificatiestatus in de audit-log | Versturen van berichten of ophalen van afspraken |

**Bewijs via test:** `ArchitectureComplianceTest#service_laag_mag_niet_afhangen_van_controller_laag`  ArchUnit verifieert dat de servicelaag geen afhankelijkheden heeft op de controllerlaag.

---

## 5. Separation of Concerns  Layered Architecture

**Definitie:** Elk architectuurlaag heeft een afgebakende verantwoordelijkheid. Lagen communiceren alleen via gedefinieerde interfaces, nooit dwars door lagen heen.

**Architectuurlagen in dit systeem:**

```
┌─────────────────────────────────────────────┐
│  Controller (HTTP-presentatie)              │  NotificationController
├─────────────────────────────────────────────┤
│  Service (bedrijfslogica)                   │  AppointmentService, DataService
├─────────────────────────────────────────────┤
│  Messaging (wachtrij)                       │  RabbitMQConsumer, RabbitMQProducer
├─────────────────────────────────────────────┤
│  Provider (externe integratie)              │  SwiftSendProvider, LegacyLinkProvider, ...
├─────────────────────────────────────────────┤
│  DAO / Repository (persistentie)            │  NotificationLogDAO, TenantDAO
└─────────────────────────────────────────────┘
```

**Bewijs via ADR:** [ADR-1](ADR/ADR-1.md)  keuze voor standalone applicatie garandeert volledige controle over de laagindeling.

**Bewijs via test:** `ArchitectureComplianceTest#dao_implementaties_zitten_in_impl_subpackage` - ArchUnit verifieert dat DAO-implementaties in het juiste subpackage zitten.

---

## Relatie met eisen

| Principe | Gerelateerde eis | ADR |
|---|---|---|
| OCP (providers uitbreidbaar) | NFR 12: uitbreidbaar voor andere OpenMRS modules | ADR-8 |
| Strategy (provider-selectie per tenant) | FR 3: gebruik van één van vier providers per tenant | ADR-8 |
| Idempotency (geen dubbele notificaties) | NFR 7: fallback/retry bij downtime | ADR-7 |
| SRP (auditlogging gescheiden) | FR 2: logging van verstuurde notificaties per organisatie | ADR-3 |
| Layered architecture (standalone) | NFR 1: multi-tenancy zonder veiligheidsrisico's | ADR-1 |
| AES-256 encryptie (converter) | NFR 5: AES-256 opslag van gevoelige data | ADR-5 |
