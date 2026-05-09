@echo off
REM Webhook Test Script - Test appointment events with HMAC-SHA256 signatures
REM This script demonstrates how OpenMRS would send appointment change events

setlocal enabledelayedexpansion
set BASE_URL=http://localhost:8080
set TENANT_ID=tenant-001
set WEBHOOK_SECRET=webhook-secret-001-for-hmac

echo.
echo ==========================================
echo ATx2.4 Webhook Test - Appointment Events
echo ==========================================
echo.

REM Note: PowerShell is used for HMAC computation
REM Creating sample appointment event

echo 1. Testing: New Appointment Created
echo ====================================

set APPOINTMENT_PAYLOAD=^{^"eventId^":^"evt-001-!random!^",^"appointmentId^":^"apt-12345^",^"appointmentUuid^":^"550e8400-e29b-41d4-a716-446655440000^",^"patientId^":^"P-98765^",^"patientName^":^"John Doe^",^"appointmentDateTime^":^"2026-05-10T14:30:00^",^"status^":^"SCHEDULED^",^"changeType^":^"CREATED^",^"providerId^":^"doc-001^",^"providerName^":^"Dr. Smith^",^"location^":^"Clinic A^",^"eventOccurredAt^":^"2026-05-08T10:00:00^"^}

set EVENT_ID=evt-001-%RANDOM%

echo Sending appointment event to: POST %BASE_URL%/api/events/appointment
echo With headers:
echo   X-Tenant-Id: %TENANT_ID%
echo   X-Event-Id: %EVENT_ID%
echo.

REM PowerShell command to compute HMAC-SHA256
powershell -NoProfile -Command ^
  "$payload = '%APPOINTMENT_PAYLOAD%'; $secret = '%WEBHOOK_SECRET%'; $hmac = [System.Text.Encoding]::UTF8.GetBytes($payload); $key = [System.Text.Encoding]::UTF8.GetBytes($secret); $mac = New-Object System.Security.Cryptography.HMACSHA256 -ArgumentList $key; $hash = $mac.ComputeHash($hmac); $hex = -join ($hash ^| ForEach-Object { $_.ToString('x2') }); Invoke-RestMethod -Uri '%BASE_URL%/api/events/appointment' -Method POST -Headers @{'Content-Type'='application/json'; 'X-Tenant-Id'='%TENANT_ID%'; 'X-Event-Id'='%EVENT_ID%'; 'X-HMAC-SHA256'=$hex} -Body $payload ^| ConvertTo-Json" 

echo.
echo.

echo 2. Testing: Webhook Health Check
echo =================================

powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/events/health' -Method GET | ConvertTo-Json"

echo.
echo.

echo ==========================================
echo Webhook tests completed!
echo ==========================================
echo.
echo Next steps:
echo 1. Check RabbitMQ Management: http://localhost:15672 (guest/guest)
echo 2. View 'appointment.events' queue for message count
echo 3. Monitor logs: docker-compose logs -f app
echo.
pause
