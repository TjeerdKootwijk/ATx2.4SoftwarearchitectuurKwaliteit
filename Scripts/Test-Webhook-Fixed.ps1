# PowerShell Webhook Test Script - Fixed HMAC
# Uses proper PowerShell syntax for HMACSHA256

$BaseUrl = "http://localhost:8080"
$TenantId = "tenant-001"
$Secret = "webhook-secret-001-for-hmac"

function GenerateHmacSignature($payload, $secret) {
    $secretBytes = [System.Text.Encoding]::UTF8.GetBytes($secret)
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
    
    # Correct PowerShell syntax for HMACSHA256
    $hmac = [System.Security.Cryptography.HMACSHA256]::new($secretBytes)
    $hash = $hmac.ComputeHash($payloadBytes)
    $signature = [System.BitConverter]::ToString($hash) -replace '-', ''
    $signature = $signature.ToLower()
    return $signature
}

# Stap 1: Health Check
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "1. Health Check" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/api/events/health" -Method GET
    Write-Host "[OK] Health OK" -ForegroundColor Green
    Write-Host $health | ConvertTo-Json
} catch {
    Write-Host "[FAILED] Health Check Failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit
}

Write-Host ""

# Stap 2: Test Appointment Created
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "2. Test: New Appointment Created" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

$EventId = "evt-001-$(Get-Date -Format 'yyyyMMddHHmmss')-$([Random]::new().Next(1000))"

$Payload = @{
    eventId = $EventId
    appointmentId = "apt-12345"
    appointmentUuid = "550e8400-e29b-41d4-a716-446655440000"
    patientId = "P-98765"
    patientName = "John Doe"
    appointmentDateTime = "2026-05-10T14:30:00"
    status = "SCHEDULED"
    changeType = "CREATED"
    providerId = "doc-001"
    providerName = "Dr. Smith"
    location = "Clinic A"
    eventOccurredAt = "2026-05-08T10:00:00"
} | ConvertTo-Json -Compress

$Signature = GenerateHmacSignature $Payload $Secret

Write-Host "Event ID: $EventId"
Write-Host "Signature: $Signature"
Write-Host ""

try {
    $Response = Invoke-RestMethod `
        -Uri "$BaseUrl/api/events/appointment" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id" = $TenantId
            "X-Event-Id" = $EventId
            "X-HMAC-SHA256" = $Signature
        } `
        -Body $Payload

    Write-Host "[OK] Request Accepted (202)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    Write-Host ($Response | ConvertTo-Json)
}
catch {
    Write-Host "[FAILED] Request Failed" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "Message: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""

# Stap 3: Test Appointment Updated
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "3. Test: Appointment Updated" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

$EventId2 = "evt-002-$(Get-Date -Format 'yyyyMMddHHmmss')-$([Random]::new().Next(1000))"

$Payload2 = @{
    eventId = $EventId2
    appointmentId = "apt-12345"
    appointmentUuid = "550e8400-e29b-41d4-a716-446655440000"
    patientId = "P-98765"
    patientName = "John Doe"
    appointmentDateTime = "2026-05-10T15:00:00"
    status = "SCHEDULED"
    changeType = "UPDATED"
    providerId = "doc-001"
    providerName = "Dr. Smith"
    location = "Clinic B"
    eventOccurredAt = "2026-05-08T11:00:00"
} | ConvertTo-Json -Compress

$Signature2 = GenerateHmacSignature $Payload2 $Secret

Write-Host "Event ID: $EventId2"
Write-Host "Signature: $Signature2"
Write-Host ""

try {
    $Response = Invoke-RestMethod `
        -Uri "$BaseUrl/api/events/appointment" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id" = $TenantId
            "X-Event-Id" = $EventId2
            "X-HMAC-SHA256" = $Signature2
        } `
        -Body $Payload2

    Write-Host "[OK] Request Accepted (202)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    Write-Host ($Response | ConvertTo-Json)
}
catch {
    Write-Host "[FAILED] Request Failed" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "Message: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""

# Stap 4: Check RabbitMQ
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "1. Open RabbitMQ: http://localhost:15672 (guest/guest)" -ForegroundColor Yellow
Write-Host "2. Check queue: appointment.events (should have 2 messages)" -ForegroundColor Yellow
Write-Host "3. View logs: docker-compose logs -f app" -ForegroundColor Yellow
Write-Host ""
Write-Host "Script completed successfully!" -ForegroundColor Green
