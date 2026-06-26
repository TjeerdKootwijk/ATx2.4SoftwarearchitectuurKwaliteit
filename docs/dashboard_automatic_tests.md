# Dashboard automatische tests

Alle automatische tests rond het dashboard op één plek. 51 tests, verdeeld over 10 klassen.
Geen draaiende Grafana of stack nodig: ze lezen de echte dashboard-, alert- en configbestanden
en testen de echte backend-code.

## Hoe run je ze

Tests draaien in Docker (JDK 21), vanuit de projectmap.

Alle dashboard tests:

```
docker run --rm -v "%cd%:/app" -w /app gradle:8.10-jdk21 gradle test --tests "*dashboard*"
```

Eén klasse (voorbeeld):

```
docker run --rm -v "%cd%:/app" -w /app gradle:8.10-jdk21 gradle test --tests "*RabbitMqDashboardTest"
```

De uitslag zie je direct in je terminal.

### Snel draaien (alleen de telling)

Voor enkel de dashboard unit-tests met een korte uitslag. Eén regel in cmd:

```
docker run --rm -v "%cd%:/app" -w /app gradle:8.10-jdk21 gradle cleanTest test --tests "*.dashboard.*" --console=plain 2>nul | findstr /C:"Totaal" /C:"Geslaagd" /C:"Mislukt" /C:"Overgeslagen"
```

Uitvoer:

```
  Totaal      : 51
  Geslaagd    : 51
  Overgeslagen: 0
  Mislukt     : 0
```

## Overzicht van de klassen

| Klasse | Wat het bewaakt | Tests |
|---|---|---|
| RabbitMqDashboardTest | Het RabbitMQ dashboard (rabbitmq.json) | 9 |
| ErrorTracingDashboardTest | Het foutopsporing dashboard (error-tracing.json) | 8 |
| AlertRulesTest | De Grafana alert regels | 9 |
| GrafanaProvisioningTest | Datasources en koppeling in docker-compose | 5 |
| LogbackMdcPatternTest | Logpatroon dat het dashboard voedt | 2 |
| NotificationMetricsDashboardTest | De metric waarde achter de transactie panelen | 3 |
| UnknownTenantDashboardTest | Foutlog bij onbekende tenant | 3 |
| TenantPollFailureDashboardTest | Foutlog bij mislukte poll | 2 |
| NotificationDiagnosticContextTest | Niet-PII logcontext die het dashboard toont | 6 |
| RabbitMQConsumerTest | Herleidbaar loggen van fouten | 4 |

## RabbitMqDashboardTest

Checkt of het RabbitMQ dashboard klopt zonder dat Grafana draait.

| Nr | Test | Korte uitleg |
|---|---|---|
| 1 | dashboard_isValidJsonWithStableUid | Geldige JSON met vaste uid rabbitmq-overview |
| 2 | everyPanel_hasUniqueIdAndTitle | Elk paneel heeft uniek id en titel |
| 3 | logsPanels_useLoki_metricPanels_usePrometheus | Logpanelen op Loki, metricpanelen op Prometheus |
| 4 | hasDeadLetterQueuePanel | Er is een paneel voor de dead letter queue |
| 5 | hasSuccessAndFailedTransactionPanels | Panelen voor geslaagde en mislukte transacties bestaan |
| 6 | hasProviderRateLimitPanel | Er is een paneel voor 429 rate limiting |
| 7 | hasErrorAndWarnLogsPanel | Er is een logpaneel dat op ERROR en WARN filtert |
| 8 | prometheusDatasource_isActuallyProvisioned | De datasource prometheus-rabbitmq bestaat echt in de provisioning |
| 9 | everyJobLabelMatchesARealPrometheusScrapeJob | Elk job label in de queries bestaat echt in prometheus.yml |

## ErrorTracingDashboardTest

Checkt of het foutopsporing dashboard klopt.

| Nr | Test | Korte uitleg |
|---|---|---|
| 10 | dashboard_isValidJsonWithStableUid | Geldige JSON met vaste uid error-tracing |
| 11 | dashboard_hasAutoRefreshAndTimeRange | Auto refresh en tijdbereik staan ingesteld |
| 12 | everyPanel_hasUniqueIdTitleAndSize | Elk paneel heeft uniek id, titel en afmeting |
| 13 | everyDataPanel_usesTheProvisionedLokiDatasource | Elk paneel gebruikt de loki-app datasource |
| 14 | logsPanel_existsAndFiltersAppErrors | Logpaneel filtert op de app en op foutregels |
| 15 | logsPanel_hasLogDetailsEnabled | Logdetails aan, nieuwste fouten bovenaan |
| 16 | dashboard_hasAnErrorCountOverview | Een paneel telt het aantal fouten |
| 17 | helpPanel_explainsTheTraceWorkflow | Uitlegpaneel beschrijft de trace_id stap naar Tempo |

## AlertRulesTest

Checkt de alert regels (het actieve deel van het dashboard).

| Nr | Test | Korte uitleg |
|---|---|---|
| 18 | allExpectedAlertsArePresent | Alle 7 verwachte alerts bestaan nog |
| 19 | everyRule_hasTitleConditionAndExpr | Elke alert heeft titel, condition en query |
| 20 | dlqAlert_watchesTheDeadLetterQueue | DLQ alert kijkt naar de dead letter queue |
| 21 | rateLimitAlert_watchesHttp429 | Rate limit alert kijkt naar HTTP 429 |
| 22 | providerErrorAlert_watchesHttp5xx | Provider storing alert kijkt naar HTTP 5xx |
| 23 | retryQueueAlert_watchesRetryQueuesAboveTwenty | Retry alert kijkt naar de retry queues, drempel boven 20 |
| 24 | downAlerts_watchTheUpMetricPerTarget | App down en rabbitmq down alerts kijken naar de up metric |
| 25 | veelMisluktAlert_usesTheFailedNotificationCounter | Mislukkings alert gebruikt de failed counter |
| 26 | veelMisluktAlert_firesAboveFiveFailures | Drempel staat op meer dan 5 mislukkingen |

## GrafanaProvisioningTest

Checkt de datasources en de mounts in docker-compose.

| Nr | Test | Korte uitleg |
|---|---|---|
| 27 | lokiDatasource_pointsAtLokiOnPort3100 | Loki datasource wijst naar poort 3100 |
| 28 | lokiDatasource_linksTraceIdToTempo | trace_id linkt door naar Tempo |
| 29 | tempoDatasource_existsAndLinksBackToLoki | Tempo bestaat en linkt terug naar Loki |
| 30 | dockerCompose_mountsLokiTempoDatasourceIntoLgtm | Datasource wordt correct gemount |
| 31 | dashboardJson_isReachableByTheGrafanaDashboardProvider | Dashboard wordt door Grafana geladen |

## LogbackMdcPatternTest

Checkt het logpatroon dat de logpanelen voedt.

| Nr | Test | Korte uitleg |
|---|---|---|
| 32 | pattern_exposesEveryDiagnosticContextKey | Patroon toont alle context sleutels |
| 33 | pattern_exposesTraceIdForTempoCorrelation | Patroon toont trace_id voor de Tempo koppeling |

## NotificationMetricsDashboardTest

Checkt of de metric waarde achter de transactie panelen klopt.

| Nr | Test | Korte uitleg |
|---|---|---|
| 34 | geslaagdeBezorging_verhoogtSuccesCounterMetProviderTag | Geslaagde bezorging zet success counter op 1 |
| 35 | providerWeigering_verhoogtMisluktCounter | Provider weigering zet failed counter op 1 |
| 36 | onverwachteCrash_teltNietAlsMisluktTransactie | Een crash hoogt de counter niet op, alleen de foutlog |

## UnknownTenantDashboardTest

Checkt het foutlog gedrag bij een onbekende tenant.

| Nr | Test | Korte uitleg |
|---|---|---|
| 37 | onbekendeTenant_logtErrorMetTenantId | Onbekende tenant geeft ERROR log met tenantId |
| 38 | onbekendeTenant_publiceertGeenNotificatie | Er wordt geen notificatie gepubliceerd |
| 39 | bekendeTenant_publiceert_enLogtGeenError | Bekende tenant publiceert en geeft geen ERROR |

## TenantPollFailureDashboardTest

Checkt het foutlog gedrag bij een mislukte poll.

| Nr | Test | Korte uitleg |
|---|---|---|
| 40 | mislukteTenantPoll_logtErrorMetTenantId | Mislukte poll geeft ERROR log met tenantId |
| 41 | foutBijEenTenant_blokkeertHetPollenVanAndereTenantsNiet | Eén kapotte tenant blokkeert de rest niet |

## NotificationDiagnosticContextTest

Checkt de niet-PII context die als logdetail op het dashboard verschijnt.

| Nr | Test | Korte uitleg |
|---|---|---|
| 42 | open_putsNonPiiContextOnMdc | Juiste niet-PII velden komen op de MDC |
| 43 | open_neverExposesPersonalData | Persoonsgegevens komen nooit in de context (NFR4/NFR11) |
| 44 | close_clearsContextSoItDoesNotLeak | Context is na sluiten leeg |
| 45 | close_restoresPreviousValue | Vorige waarde wordt hersteld |
| 46 | open_setsExactlyTheExpectedNonPiiKeySet | Precies de verwachte sleutels, niet meer |
| 47 | open_skipsZeroAttemptAndNullFields | Lege velden en poging 0 worden overgeslagen |

## RabbitMQConsumerTest

Checkt dat fouten herleidbaar gelogd worden (de regels die het foutpaneel toont).

| Nr | Test | Korte uitleg |
|---|---|---|
| 48 | unexpectedException_isLoggedWithStacktraceAndInputContext | Fout gelogd met stacktrace en context, zonder PII |
| 49 | providerFailureResult_isLoggedWithInputContext | Provider weigering gelogd met context |
| 50 | diagnosticContext_isClearedAfterProcessing | MDC leeg na verwerking, geen lekkage |
| 51 | deserializationFailure_isLoggedAndRethrown | Kapotte payload één keer gelogd en doorgegooid |
