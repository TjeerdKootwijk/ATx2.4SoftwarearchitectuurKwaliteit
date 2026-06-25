# Dashboard tests (automatisch)

Overzicht van alle automatische tests rond het dashboard. 37 tests, verdeeld over 8 klassen.

## Hoe run je ze

Tests draaien in Docker (JDK 21), vanuit de projectmap.

Alle dashboard tests:

```
docker run --rm -v "${PWD}:/app" -w /app gradle:8.10-jdk21 gradle test --tests "*dashboard*"
```

Eén klasse (voorbeeld):

```
docker run --rm -v "${PWD}:/app" -w /app gradle:8.10-jdk21 gradle test --tests "*RabbitMqDashboardTest"
```

Rapport na afloop: `build/reports/tests/test/index.html`.

## Wat checken de klassen

| Klasse | Wat het bewaakt |
|---|---|
| RabbitMqDashboardTest | Het RabbitMQ dashboard (rabbitmq.json) |
| ErrorTracingDashboardTest | Het foutopsporing dashboard (error-tracing.json) |
| AlertRulesTest | De Grafana alert regels |
| GrafanaProvisioningTest | Datasources en koppeling in docker-compose |
| LogbackMdcPatternTest | Logpatroon dat het dashboard voedt |
| NotificationMetricsDashboardTest | De metric waarde achter de transactie panelen |
| UnknownTenantDashboardTest | Foutlog bij onbekende tenant |
| TenantPollFailureDashboardTest | Foutlog bij mislukte poll |

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
| 8 | everyJobLabelMatchesARealPrometheusScrapeJob | Elk job label in de queries bestaat echt in prometheus.yml |

## ErrorTracingDashboardTest

Checkt of het foutopsporing dashboard klopt.

| Nr | Test | Korte uitleg |
|---|---|---|
| 9 | dashboard_isValidJsonWithStableUid | Geldige JSON met vaste uid error-tracing |
| 10 | dashboard_hasAutoRefreshAndTimeRange | Auto refresh en tijdbereik staan ingesteld |
| 11 | everyPanel_hasUniqueIdTitleAndSize | Elk paneel heeft uniek id, titel en afmeting |
| 12 | everyDataPanel_usesTheProvisionedLokiDatasource | Elk paneel gebruikt de loki-app datasource |
| 13 | logsPanel_existsAndFiltersAppErrors | Logpaneel filtert op de app en op foutregels |
| 14 | logsPanel_hasLogDetailsEnabled | Logdetails aan, nieuwste fouten bovenaan |
| 15 | dashboard_hasAnErrorCountOverview | Een paneel telt het aantal fouten |
| 16 | helpPanel_explainsTheTraceWorkflow | Uitlegpaneel beschrijft de trace_id stap naar Tempo |

## GrafanaProvisioningTest

Checkt de datasources en de mounts in docker-compose.

| Nr | Test | Korte uitleg |
|---|---|---|
| 17 | lokiDatasource_pointsAtLokiOnPort3100 | Loki datasource wijst naar poort 3100 |
| 18 | lokiDatasource_linksTraceIdToTempo | trace_id linkt door naar Tempo |
| 19 | tempoDatasource_existsAndLinksBackToLoki | Tempo bestaat en linkt terug naar Loki |
| 20 | dockerCompose_mountsLokiTempoDatasourceIntoLgtm | Datasource wordt correct gemount |
| 21 | dashboardJson_isReachableByTheGrafanaDashboardProvider | Dashboard wordt door Grafana geladen |

## AlertRulesTest

Checkt de alert regels (het actieve deel van het dashboard).

| Nr | Test | Korte uitleg |
|---|---|---|
| 22 | allExpectedAlertsArePresent | Alle 7 verwachte alerts bestaan nog |
| 23 | everyRule_hasTitleConditionAndExpr | Elke alert heeft titel, condition en query |
| 24 | dlqAlert_watchesTheDeadLetterQueue | DLQ alert kijkt naar de dead letter queue |
| 25 | rateLimitAlert_watchesHttp429 | Rate limit alert kijkt naar HTTP 429 |
| 26 | veelMisluktAlert_usesTheFailedNotificationCounter | Mislukkings alert gebruikt de failed counter |
| 27 | veelMisluktAlert_firesAboveFiveFailures | Drempel staat op meer dan 5 mislukkingen |

## LogbackMdcPatternTest

Checkt het logpatroon dat de logpanelen voedt.

| Nr | Test | Korte uitleg |
|---|---|---|
| 28 | pattern_exposesEveryDiagnosticContextKey | Patroon toont alle context sleutels |
| 29 | pattern_exposesTraceIdForTempoCorrelation | Patroon toont trace_id voor de Tempo koppeling |

## NotificationMetricsDashboardTest

Checkt of de metric waarde achter de transactie panelen klopt.

| Nr | Test | Korte uitleg |
|---|---|---|
| 30 | geslaagdeBezorging_verhoogtSuccesCounterMetProviderTag | Geslaagde bezorging zet success counter op 1 |
| 31 | providerWeigering_verhoogtMisluktCounter | Provider weigering zet failed counter op 1 |
| 32 | onverwachteCrash_teltNietAlsMisluktTransactie | Een crash hoogt de counter niet op, alleen de foutlog |

## UnknownTenantDashboardTest

Checkt het foutlog gedrag bij een onbekende tenant.

| Nr | Test | Korte uitleg |
|---|---|---|
| 33 | onbekendeTenant_logtErrorMetTenantId | Onbekende tenant geeft ERROR log met tenantId |
| 34 | onbekendeTenant_publiceertGeenNotificatie | Er wordt geen notificatie gepubliceerd |
| 35 | bekendeTenant_publiceert_enLogtGeenError | Bekende tenant publiceert en geeft geen ERROR |

## TenantPollFailureDashboardTest

Checkt het foutlog gedrag bij een mislukte poll.

| Nr | Test | Korte uitleg |
|---|---|---|
| 36 | mislukteTenantPoll_logtErrorMetTenantId | Mislukte poll geeft ERROR log met tenantId |
| 37 | foutBijEenTenant_blokkeertHetPollenVanAndereTenantsNiet | Eén kapotte tenant blokkeert de rest niet |
