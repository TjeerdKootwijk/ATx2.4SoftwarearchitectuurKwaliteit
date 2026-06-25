### Unit tests - Foutopsporing-dashboard

Tests die het dashboard en de logging-keten bewaken (zonder draaiende stack).

---

#### `ErrorTracingDashboardTest` - `error-tracing.json`

| Test | Controleert |
| --- | --- |
| `dashboard_isValidJsonWithStableUid` | Geldige JSON met vaste uid `error-tracing`. |
| `dashboard_hasAutoRefreshAndTimeRange` | Auto-refresh en tijdbereik ingesteld. |
| `everyPanel_hasUniqueIdTitleAndSize` | Elk paneel heeft uniek id, titel en afmeting. |
| `everyDataPanel_usesTheProvisionedLokiDatasource` | Elk paneel gebruikt datasource `loki-app`. |
| `logsPanel_existsAndFiltersAppErrors` | Logs-paneel filtert op de app én op foutregels. |
| `logsPanel_hasLogDetailsEnabledSo...` | Log-details aan; nieuwste fouten bovenaan. |
| `dashboard_hasAnErrorCountOverview` | Een paneel telt het aantal fouten. |
| `helpPanel_explainsTheTraceWorkflow` | Uitlegpaneel beschrijft de trace_id -> Tempo-werkwijze. |

---

#### `GrafanaProvisioningTest` - datasources & docker-compose

| Test | Controleert |
| --- | --- |
| `lokiDatasource_pointsAtLokiOnPort3100` | Loki-datasource wijst naar poort 3100. |
| `lokiDatasource_linksTraceIdToTempo` | `trace_id` linkt door naar Tempo. |
| `tempoDatasource_existsAndLinksBackToLoki` | Tempo bestaat en linkt terug naar Loki. |
| `dockerCompose_mountsLokiTempoDatasourceIntoLgtm` | Datasource wordt correct gemount. |
| `dashboardJson_isReachableByTheGrafanaDashboardProvider` | Dashboard wordt door Grafana geladen. |

---

#### `LogbackMdcPatternTest` - `logback-spring.xml`

| Test | Controleert |
| --- | --- |
| `pattern_exposesEveryDiagnosticContextKey` | Pattern toont alle MDC-sleutels. |
| `pattern_exposesTraceIdForTempoCorrelation` | Pattern toont `trace_id` voor Tempo-koppeling. |

---

#### `NotificationDiagnosticContextTest` - niet-PII context op MDC

| Test | Controleert |
| --- | --- |
| `open_putsNonPiiContextOnMdc` | Juiste niet-PII velden komen op de MDC. |
| `open_neverExposesPersonalData` | Persoonsgegevens komen nooit in de context (NFR4/NFR11). |
| `close_clearsContextSoItDoesNotLeak` | Context is na sluiten leeg. |
| `close_restoresPreviousValue` | Vorige waarde wordt hersteld. |
| `open_setsExactlyTheExpectedNonPiiKeySet` | Precies de verwachte sleutels, niet meer. |
| `open_skipsZeroAttemptAndNullFields` | Lege velden en poging 0 worden overgeslagen. |

---

#### `RabbitMQConsumerTest` - herleidbaar loggen

| Test | Controleert |
| --- | --- |
| `unexpectedException_isLoggedWithStacktraceAndInputContext` | Fout gelogd met stacktrace + context, zonder PII. |
| `providerFailureResult_isLoggedWithInputContext` | Provider-weigering gelogd met context. |
| `diagnosticContext_isClearedAfterProcessing` | MDC leeg na verwerking (geen lekkage). |
| `deserializationFailure_isLoggedAndRethrown` | Kapotte payload één keer gelogd en doorgegooid. |

---

### Backend-tests gekoppeld aan de FMEA

Tests die de **waarde achter een dashboardpaneel** aantonen: de backend produceert de metric
of de logregel die het dashboard laat zien. Zo bewijzen we automatisch dat het dashboard het
juiste beeld toont bij de FMEA-scenario's.

#### `NotificationMetricsDashboardTest` - FMEA 17/18 (monitoring) + alert *Veel mislukte notificaties*

Voedt: panelen *Geslaagde/Mislukte/Totaal Transacties*, *Verstuurd vs Mislukt*,
*Notificatie Slagingspercentage* (metric `notifications_sent_total`).

| Test | Controleert |
| --- | --- |
| `geslaagdeBezorging_verhoogtSuccesCounterMetProviderTag` | Geslaagde bezorging → `status=success`-counter = 1 (met provider-tag). |
| `providerWeigering_verhoogtMisluktCounter` | Provider-weigering → `status=failed`-counter = 1. |
| `onverwachteCrash_teltNietAlsMisluktTransactie_alleenAlsFoutlog` | Een crash hoogt de transactie-counter niet op; verschijnt enkel in het foutlog-paneel. |

#### `UnknownTenantDashboardTest` - FMEA 13 (onbekende tenantId)

Voedt: paneel *Errors & Warnings* (`detected_level=~"error|warn"`).

| Test | Controleert |
| --- | --- |
| `onbekendeTenant_logtErrorMetTenantId_zichtbaarOpDashboard` | Onbekende tenant → ERROR-log mét tenantId (herleidbaar op dashboard). |
| `onbekendeTenant_publiceertGeenNotificatie` | Geen notificatie gepubliceerd voor de onbekende tenant. |
| `bekendeTenant_publiceert_enLogtGeenError` | Positieve controle: bekende tenant → publiceert, geen ERROR op dashboard. |

#### `TenantPollFailureDashboardTest` - FMEA 11 (credential-rotatie) + raakt 12 (geen starvation)

Voedt: paneel *Errors & Warnings* + poll-fout-alert.

| Test | Controleert |
| --- | --- |
| `mislukteTenantPoll_logtErrorMetTenantId` | Mislukte poll (bv. 401 na key-rotatie) → ERROR-log mét tenantId. |
| `foutBijEenTenant_blokkeertHetPollenVanAndereTenantsNiet` | Eén kapotte tenant blokkeert het pollen van de overige tenants niet. |
