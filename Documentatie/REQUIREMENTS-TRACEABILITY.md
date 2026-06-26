# Requirements Traceability Matrix

OpenMRS Communicatiemodule - ATx2.4 - Groep C1

---

## Doel van dit document

Dit document toont hoe elke eis van het systeem wordt gedekt door de architectuur. Voor elke eis is zichtbaar welke architectuurbeslissing (ADR) hem rechtvaardigt, welke klasse hem implementeert, en welke test bewijst dat de implementatie correct werkt. Zo is altijd aantoonbaar dat het systeem aan zijn eisen voldoet - niet alleen op papier, maar ook in code.

De matrix is opgedeeld in vier secties: functionele eisen, niet-functionele eisen, architectuurprincipes, en het retry- en DLQ-mechanisme. Elke sectie heeft een korte toelichting over waarom die eisen bij elkaar horen.

---

## 1. Functionele eisen (FR)

Functionele eisen beschrijven wat het systeem moet doen vanuit het perspectief van de gebruiker of opdrachtgever. In dit systeem gaat het om het ophalen van afspraken, het versturen van notificaties, het bijhouden van een auditlog, en het correct omgaan met meerdere tenants en providers.

De keuze voor de technologiestack (ADR-2) en de integratiemethode via polling (ADR-3) vormen de basis voor de meeste functionele eisen. De provider-factory (ADR-8) maakt het mogelijk dat elke tenant een eigen communicatiekanaal gebruikt zonder dat de verwerkinsglaag daarvoor hoeft te worden aangepast.

| Eis | Omschrijving | ADR | Implementatieklasse | Test |
|-----|-------------|-----|--------------------|----|
| FR1 | Notificaties versturen naar patienten via externe providers | [ADR-2](../ADR/ADR-2.md), [ADR-8](../ADR/ADR-8.md) | `RabbitMQConsumer`, `MessagingProviderFactory`, `SwiftSendProvider`, `LegacyLinkProvider`, `AsyncFlowProvider`, `SecurePostProvider` | `SystemTest#notificatie_wordt_verstuurd_via_provider` |
| FR2 | Auditlogging van alle verzonden notificaties inclusief mislukte pogingen | [ADR-6](../ADR/ADR-6.md) | `DataService`, `NotificationLogDAO`, `NotificationLogEntity` | `Fmea02RabbitMqPartitieTest#audit_log_opgeslagen_voor_retry_poging_ook_bij_rabbitmq_partitie` |
| FR3 | Per-tenant keuze van communicatieprovider (SwiftSend / LegacyLink / AsyncFlow / SecurePost) | [ADR-8](../ADR/ADR-8.md) | `MessagingProviderFactory`, `TenantConfiguration` | `SystemTest#httpEndpoint_meerdereProviderTypes_gevenAllemalAccepted`, `Fmea05OnbekendeTenantTest#onbekende_provider_naam_gooit_provider_not_found_exception` |
| FR4 | Periodiek ophalen van afspraken via OpenMRS REST API (polling elke 60 seconden per tenant) | [ADR-3](../ADR/ADR-3.md), [ADR-4](../ADR/ADR-4.md) | `PollingJob`, `OpenMrsRestAppointmentFetcher` | `Fmea01OpenmrsOfflineTest#openmrs_offline_geeft_lege_lijst_terug`, `Fmea13OpenmrsTimeoutTest#openmrs_timeout_geeft_lege_lijst_binnen_10_seconden` |
| FR5 | Idempotente verwerking - geen dubbele notificaties bij herstart of herhaalde polling | [ADR-4](../ADR/ADR-4.md) | `IdempotencyService`, `ProcessedEventDAO`, `ProcessedEventJpaRepository` | `Fmea11DataRetentieTest#events_ouder_dan_14_dagen_worden_verwijderd` |

---

## 2. Niet-functionele eisen (NFR)

Niet-functionele eisen beschrijven hoe het systeem zich moet gedragen onder omstandigheden zoals uitval, hoge belasting, beveiligingsaanvallen of privacywetgeving. Ze zijn vaak moeilijker aantoonbaar dan functionele eisen, juist daarom is per NFR een specifieke test aangewezen die het gedrag onder die omstandigheid verifieert.

De zwaarste NFRs in dit systeem zijn NFR5 (encryptie van credentials), NFR7 (fault-tolerance bij uitval) en NFR12 (geen patiëntdata buiten de verwerkinsglaag). Deze drie eisen hebben de meeste testsupport gekregen omdat de gevolgen van een schending het grootst zijn: een datalek, een systeem dat stil valt of een AVG-overtreding.

| Eis | Omschrijving | ADR | Implementatieklasse | Test |
|-----|-------------|-----|--------------------|----|
| NFR1 | Multi-tenant: meerdere onafhankelijke OpenMRS-instanties zonder data-interferentie | [ADR-1](../ADR/ADR-1.md) | `TenantEntity`, `TenantDAO`, `TenantConfiguration` | `Fmea14MultiTenantTest#berichten_van_meerdere_tenants_worden_onafhankelijk_geauditlogd`, `Fmea14MultiTenantTest#actieve_tenant_wordt_herkend_als_geldig` |
| NFR3 | Uitbreidbaar met nieuwe communicatieproviders zonder bestaande code te wijzigen (OCP) | [ADR-8](../ADR/ADR-8.md) | `MessagingProvider` (interface), `MessagingProviderFactory` | `ArchitectureComplianceTest#alle_concrete_providers_implementeren_messaging_provider_interface` |
| NFR4 | Compatibel met OpenMRS 2.7.x en hoger; ontbrekend endpoint mag de module niet laten crashen | [ADR-10](../ADR/ADR-10.md) | `OpenMrsRestAppointmentFetcher`, `VersionCompatibilityChecker` | `Fmea01OpenmrsOfflineTest#openmrs_500_fout_geeft_lege_lijst_terug` |
| NFR5 | AES-256-GCM encryptie van gevoelige credentials (API-keys, wachtwoorden) in de database | [ADR-2](../ADR/ADR-2.md) | `AesEncryptionConverter`, `TenantEntity` (`@Convert`) | `SecurityTest#plaintext_niet_opgeslagen_in_database`, `SecurityTest#encryptie_is_reversibel`, `SecurityTest#elke_encryptie_heeft_unieke_iv`, `Fmea12AesSleutelInLogsTest#audit_log_bevat_geen_aes_sleutel` |
| NFR6 | Berichttransformatie en validatie via FHIR R4 standaard voordat een notificatie verstuurd wordt | [ADR-7](../ADR/ADR-7.md) | `FhirAppointmentValidator`, `FhirR4AppointmentMapper` | `Fmea07FhirValidatieTest#lege_appointment_faalt_validatie`, `Fmea07FhirValidatieTest#appointment_zonder_status_faalt_validatie`, `Fmea07FhirValidatieTest#geldige_appointment_slaagt_validatie` |
| NFR7 | Fault-tolerance: zelfstandig blijven functioneren bij tijdelijke uitval van OpenMRS of een provider | [ADR-4](../ADR/ADR-4.md), [ADR-10](../ADR/ADR-10.md) | `ExponentialBackoffRetryPolicy`, `RabbitMQConsumer`, `OpenMrsRestAppointmentFetcher` | `Fmea01OpenmrsOfflineTest`, `Fmea03ProviderRateLimitingTest`, `Fmea04CredentialRotatieTest`, `Fmea10ProviderOfflineTest`, `Fmea13OpenmrsTimeoutTest`, `Fmea15ExponentialBackoffTest` |
| NFR8 | Ondersteuning van meerdere karaktersets (Arabisch, Chinees, Cyrillisch) via UTF-8 door de gehele pipeline | [ADR-9](../ADR/ADR-9.md) | `RabbitMQConsumer`, `SwiftSendProvider`, `LegacyLinkProvider` | `CharacterEncodingTest#chinees_bericht_wordt_correct_verstuurd_via_swiftsend`, `CharacterEncodingTest#arabisch_bericht_wordt_correct_verstuurd_via_legacylink`, `CharacterEncodingTest#cyrillisch_bericht_wordt_correct_verstuurd_via_swiftsend` |
| NFR9 | Observability: real-time inzicht in berichtstatus en applicatiegezondheid via Prometheus en actuator | [ADR-5](../ADR/ADR-5.md) | `MeterRegistry` (Micrometer), Spring Boot Actuator | `Fmea08ActuatorHealthTest#actuator_health_endpoint_bereikbaar_voor_tls_certificaat_monitoring`, `Fmea17PrometheusTest#meter_registry_is_aanwezig_voor_prometheus_export`, `Fmea17PrometheusTest#na_verwerking_bevat_registry_notificatie_counter` |
| NFR10 | Data-retentie AVG: processed_events ouder dan 14 dagen worden automatisch verwijderd | [ADR-4](../ADR/ADR-4.md) | `DataService#cleanupExpiredData()`, `ProcessedEventDAO` | `Fmea11DataRetentieTest#events_ouder_dan_14_dagen_worden_verwijderd`, `Fmea11DataRetentieTest#recente_data_blijft_bewaard` |
| NFR11 | Data-retentie AVG: notification_logs ouder dan 1 jaar worden automatisch verwijderd | [ADR-6](../ADR/ADR-6.md) | `DataService#cleanupExpiredData()`, `NotificationLogDAO` | `Fmea11DataRetentieTest#logs_ouder_dan_1_jaar_worden_verwijderd` |
| NFR12 | Geen persoonlijke patiëntdata (naam, BSN, geboortedatum, adres) in RabbitMQ DLQ-berichten | [ADR-4](../ADR/ADR-4.md) | `NotificationQueueMessage` (bevat alleen UUID + tenantId) | `Fmea18PatientDataInDlqTest#notification_queue_message_bevat_geen_persoonlijke_data`, `Fmea18PatientDataInDlqTest#bericht_bevat_alleen_uuid_en_tenant_geen_patientdata` |
| NFR13 | Per-tenant tijdzone-ondersteuning via IANA timezone ID zodat notificatievensters correct worden berekend | [ADR-11](../ADR/ADR-11.md) | `TenantEntity#timezone`, `PollingJob` | `Fmea16ClockdriftUtcTest#sent_at_timestamp_ligt_tussen_voor_en_na_tijdstip`, `Fmea16ClockdriftUtcTest#standaard_tijdzone_is_utc` |

---

## 3. Architectuurprincipes

De architectuur van dit systeem is bewust gebaseerd op een aantal ontwerpprincipes. Deze principes zijn geen eisen in de traditionele zin, maar ze verklaren waarom de code eruitziet zoals hij eruitziet en waarom bepaalde ontwerpkeuzes zijn gemaakt. Ze zijn opgenomen in de traceability matrix omdat de beoordelingscriteria expliciet vragen om aantoonbare toepassing van OCP, Strategy, SRP en layering.

Elk principe wordt bewezen door een ArchUnit-test die bij elke build automatisch controleert of de architectuur nog steeds aan het principe voldoet. Zo wordt voorkomen dat toekomstige wijzigingen de architectuur onbedoeld breken.

| Principe | Gerelateerde eis | ADR | Implementatie | Test |
|----------|-----------------|-----|---------------|------|
| Open/Closed Principle (OCP) | NFR3 | [ADR-8](../ADR/ADR-8.md) | `MessagingProvider` interface - nieuwe provider toevoegen vereist geen wijziging in bestaande klassen | `ArchitectureComplianceTest#alle_concrete_providers_implementeren_messaging_provider_interface` |
| Strategy Pattern | FR3 | [ADR-8](../ADR/ADR-8.md) | `RabbitMQConsumer` selecteert de juiste provider via `MessagingProviderFactory` op basis van de tenant-configuratie, zonder switch-statements | `SystemTest#httpEndpoint_meerdereProviderTypes_gevenAllemalAccepted` |
| Idempotency | NFR7, FR5 | [ADR-4](../ADR/ADR-4.md) | `IdempotencyService` berekent een SHA-256 hash per event en slaat deze op in de `processed_events` tabel - dubbele events worden geweigerd nog voor de queue | `Fmea11DataRetentieTest#events_ouder_dan_14_dagen_worden_verwijderd` |
| Single Responsibility Principle (SRP) | FR2 | [ADR-6](../ADR/ADR-6.md) | `DataService` logt uitsluitend, `PollingJob` haalt uitsluitend op, `FhirAppointmentValidator` valideert uitsluitend - geen klasse doet meer dan een taak | `ArchitectureComplianceTest#service_laag_mag_niet_afhangen_van_controller_laag` |
| Layered Architecture | NFR1 | [ADR-1](../ADR/ADR-1.md) | Vijf lagen: Controller - Service - Messaging - Provider - DAO; communicatie alleen via interfaces, nooit dwars door lagen heen | `ArchitectureComplianceTest#dao_implementaties_zitten_in_impl_subpackage` |
| AES-256 encryptie via JPA Converter | NFR5 | [ADR-2](../ADR/ADR-2.md) | `AesEncryptionConverter` implementeert `AttributeConverter` - encryptie en decryptie gebeuren transparant, de rest van de code ziet nooit plaintext credentials | `SecurityTest#plaintext_niet_opgeslagen_in_database` |

---

## 4. Retry- en DLQ-mechanisme

Het retry- en dead-letter mechanisme is een van de kritiekste onderdelen van het systeem. Wanneer een notificatie niet kan worden verstuurd - door een tijdelijke provider-uitval, rate-limiting, of een netwerkfout - moet het systeem garanderen dat het bericht niet verloren gaat en dat de oorzaak van de fout aantoonbaar is in de auditlog.

Het mechanisme werkt in drie stappen. Eerst probeert de `RabbitMQConsumer` het bericht opnieuw via exponential backoff met vijf pogingen en oplopende wachttijden (5 seconden, 30 seconden, 2 minuten, 5 minuten, 10 minuten). Na elke mislukte poging wordt een `FAILED`-record aangemaakt in de auditlog, zodat de fout zichtbaar is ongeacht wat er daarna gebeurt. Als alle vijf pogingen zijn uitgeput, wordt het bericht naar de Dead Letter Queue (DLQ) gestuurd voor handmatige inspectie door een beheerder. Berichten worden nooit stilzwijgend verwijderd.

Een specifieke privacy-eis (NFR12) schrijft voor dat berichten in de DLQ geen persoonlijke patiëntdata mogen bevatten. Het `NotificationQueueMessage`-object bevat daarom uitsluitend een `appointmentUuid` en een `tenantId`, nooit naam, BSN, geboortedatum of adres.

| Gedrag | Gerelateerde eis | ADR | Implementatie | Test |
|--------|-----------------|-----|---------------|------|
| Exponential backoff bij provider-fout (5s - 30s - 2m - 5m - 10m) | NFR7 | [ADR-4](../ADR/ADR-4.md) | `ExponentialBackoffRetryPolicy` berekent de vertraging per poging op basis van het retry-telnummer in de AMQP-header | `Fmea15ExponentialBackoffTest#backoff_delays_zijn_strikt_oplopend`, `Fmea15ExponentialBackoffTest#vertraging_bij_eerste_poging_is_5_seconden` |
| Na maximaal 5 retries wordt het bericht naar de Dead Letter Queue gestuurd | NFR7, NFR9 | [ADR-4](../ADR/ADR-4.md) | `RabbitMQConsumer` stuurt het bericht naar `RabbitMQConfig#DEAD_LETTER_EXCHANGE` zodra `retryPolicy.shouldRetry()` false geeft | `Fmea06MaxRetriesNaarDlqTest#na_max_retries_wordt_bericht_naar_dead_letter_exchange_gestuurd`, `Fmea06MaxRetriesNaarDlqTest#voor_max_retries_wordt_bericht_naar_retry_exchange_gestuurd_niet_dlq` |
| Auditlog wordt opgeslagen voor de retry-poging, ook als RabbitMQ zelf onbereikbaar is | FR2, NFR7 | [ADR-6](../ADR/ADR-6.md) | `DataService#saveNotificationLog()` wordt aangeroepen voor de `rabbitTemplate.convertAndSend()` - volgorde gegarandeerd door code, niet door transactie | `Fmea02RabbitMqPartitieTest#audit_log_opgeslagen_voor_retry_poging_ook_bij_rabbitmq_partitie` |
| DLQ-berichten bevatten geen persoonlijke patiëntdata | NFR12 | [ADR-4](../ADR/ADR-4.md) | `NotificationQueueMessage` heeft geen velden voor naam, BSN, geboortedatum of adres - via reflectie geverifieerd | `Fmea18PatientDataInDlqTest#notification_queue_message_bevat_geen_persoonlijke_data`, `Fmea18PatientDataInDlqTest#bericht_bevat_alleen_uuid_en_tenant_geen_patientdata` |
