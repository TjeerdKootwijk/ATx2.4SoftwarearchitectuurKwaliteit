# End-to-end pipeline performance test — voert alle stappen in de juiste volgorde uit.
#
# Gebruik (vanuit een willekeurige map):
#   .\performance\run_e2e.ps1                 # 150 afspraken
#   .\performance\run_e2e.ps1 -Count 500      # zwaardere run
#   .\performance\run_e2e.ps1 -Count 150 -PurgeDeadLetter   # ook DLQ legen voor schone cijfers
#
# Het script: zet de afspraak-count, herbouwt fake-openmrs, leegt processed_events + queue,
# herstart de app (verse poll) en draait daarna de meting.

param(
    [int]$Count = 150,
    [int]$MaxWait = 300,
    [switch]$PurgeDeadLetter
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent   # projectroot (waar docker-compose.yml staat)

Write-Host "1/5  fake-openmrs herbouwen met $Count afspraken (allemaal in het 1u-venster)..." -ForegroundColor Cyan
$env:FAKE_APPOINTMENT_COUNT_1H = "$Count"
$env:FAKE_APPOINTMENT_COUNT_24H = "0"
Push-Location $root
try {
    docker-compose up -d --build fake-openmrs | Out-Null

    Write-Host "2/5  app stoppen (geeft unacked berichten vrij -> schone telling)..." -ForegroundColor Cyan
    docker-compose stop app | Out-Null

    Write-Host "3/5  processed_events + notification_logs + queues legen..." -ForegroundColor Cyan
    docker exec atx24-postgres psql -U postgres -d atx24db -c "DELETE FROM processed_events; DELETE FROM notification_logs;" | Out-Null
    docker exec atx24-rabbitmq rabbitmqctl purge_queue notification.queue | Out-Null
    if ($PurgeDeadLetter) {
        docker exec atx24-rabbitmq rabbitmqctl purge_queue notification.dead-letter.queue | Out-Null
    }

    Write-Host "4/5  app starten (triggert ~10s later een verse poll)..." -ForegroundColor Cyan
    docker-compose start app | Out-Null
}
finally {
    Pop-Location
}

Write-Host "5/5  meten (script wacht tot de app online is)..." -ForegroundColor Cyan
python "$PSScriptRoot/pipeline_e2e_measure.py" --expected $Count --max-wait $MaxWait
