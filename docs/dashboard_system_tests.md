# Dashboard systeemtest

Eén automatische test (`DashboardSystemTest`) die het **draaiende** dashboard end-to-end
controleert via de Grafana HTTP API. Dit is het dashboard-equivalent van de `SystemTest` van
de notificatieketen.

## Waarom naast de unit-tests?

De unit-tests controleren de dashboard-definitie (klopt de JSON, het alert-bestand, de config)
en de backend-waarde. Ze draaien zonder stack en zijn snel. Maar ze bewijzen **niet** dat het
draaiende Grafana alles ook echt laat werken: een query kan op papier kloppen maar live toch
niets opleveren, of een datasource kan verkeerd aangesloten zijn.

Deze systeemtest vult dat gat: hij praat met het echte Grafana en controleert per onderdeel of
het live werkt.

## Wat het test (elk deel van het dashboard)

| Nr | Test | Wat het live bewijst |
|---|---|---|
| 1 | grafana_isHealthy | Grafana draait en reageert |
| 2 | rabbitMqDashboard_isLoadedWithPanels | Het RabbitMQ-dashboard is geladen en heeft panelen |
| 3 | errorTracingDashboard_isLoaded | Het foutopsporing-dashboard is geladen |
| 4 | provisionedDatasources_exist | De datasources prometheus-rabbitmq en loki-app bestaan in Grafana |
| 5 | prometheusDatasource_isHealthy | Grafana kan Prometheus echt bereiken (datasource health = OK) |
| 6 | alertRules_areProvisioned | De alerts staan echt in Grafana geladen |
| 7 | prometheusDatasource_returnsLiveData | Een echte query levert data op, dus de panelen blijven niet leeg |

## Hoe run je het

De test heeft een **draaiende stack** nodig. Draait de stack niet, dan slaat de test zichzelf
over (skipped) en blijft de build groen. Hij breekt dus nooit je gewone testronde.

1. Start de stack vanuit de projectmap:
   ```
   docker-compose up -d
   ```
   Wacht tot alles gezond is (ongeveer 1 tot 2 minuten).

2. Zoek de naam van het Docker-netwerk van de stack (hangt af van je projectmap):
   ```
   docker network ls | findstr atx
   ```
   Bijvoorbeeld `atx24softwarearchitectuurkwaliteit_default`.

3. Draai de systeemtest in een container op datzelfde netwerk, zodat hij Grafana via de
   servicenaam `lgtm:3000` kan bereiken. `cleanTest` forceert dat de test echt opnieuw draait
   (anders gebruikt Gradle een gecachet resultaat). Eén regel in CMD:
   ```
   docker run --rm --network atx24softwarearchitectuurkwaliteit_default -e GRAFANA_URL=http://lgtm:3000 -v "%cd%:/app" -w /app gradle:8.10-jdk21 gradle cleanTest test --tests "*DashboardSystemTest" --console=plain
   ```

De uitslag zie je direct in je terminal.

De Grafana-URL is instelbaar via de omgevingsvariabele `GRAFANA_URL` (standaard
`http://localhost:3000`, handig als je ooit vanuit een IDE draait). Login is `admin` / `admin`.

### Laatste live run

Alle 7 tests groen tegen de draaiende stack (`tests=7, skipped=0, failures=0`).

## Gedrag zonder stack

Run je deze test zonder dat de stack draait (bijvoorbeeld in de gewone `*dashboard*`-ronde),
dan worden alle 7 de tests **overgeslagen** in plaats van rood. Zo blijft de snelle build
betrouwbaar en draai je de zware systeemtest alleen bewust met de stack aan.
