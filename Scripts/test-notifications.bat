@echo off
REM Example Notification Requests for ATx2.4 Communication Module
REM Usage: Run from command prompt to send test notifications

setlocal enabledelayedexpansion
set BASE_URL=http://localhost:8080

echo.
echo ==========================================
echo ATx2.4 Communication Module - Test Script
echo ==========================================
echo.

REM Check service status
echo 1. Checking service status...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications/status' -Method GET | ConvertTo-Json"
echo.
echo.

REM Patient Alert
echo 2. Sending Patient Alert...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications/receive' -Method POST -Headers @{'Content-Type'='application/json'} -Body '{\"title\":\"Patient Vital Signs Alert\",\"message\":\"Patient John Doe (ID: P-12345) vital signs have changed significantly. Blood pressure: 160/100 mmHg\",\"type\":\"ALERT\",\"source\":\"OPENMRS\",\"recipientId\":\"doctor-001\"}' | ConvertTo-Json"
echo.
echo.

REM Lab Result
echo 3. Sending Lab Result Notification...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications/receive' -Method POST -Headers @{'Content-Type'='application/json'} -Body '{\"title\":\"Lab Results Available\",\"message\":\"Blood test results for patient ID P-98765 are ready for review. Hemoglobin: 13.5 g/dL\",\"type\":\"INFO\",\"source\":\"OPENMRS\",\"recipientId\":\"lab-tech-003\"}' | ConvertTo-Json"
echo.
echo.

REM Prescription
echo 4. Sending Prescription Notification...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications' -Method POST -Headers @{'Content-Type'='application/json'} -Body '{\"title\":\"New Prescription\",\"message\":\"Prescription for patient Jane Smith: Amoxicillin 500mg, 3 times daily for 7 days\",\"type\":\"INFO\",\"source\":\"OPENMRS\",\"recipientId\":\"pharmacist-002\"}' | ConvertTo-Json"
echo.
echo.

REM Appointment Reminder
echo 5. Sending Appointment Reminder...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications/receive' -Method POST -Headers @{'Content-Type'='application/json'} -Body '{\"title\":\"Appointment Reminder\",\"message\":\"Patient Michael Johnson has a follow-up appointment tomorrow at 2:30 PM with Dr. Smith\",\"type\":\"INFO\",\"source\":\"OPENMRS\",\"recipientId\":\"clinic-staff-001\"}' | ConvertTo-Json"
echo.
echo.

REM Critical Warning
echo 6. Sending Critical Warning...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications/receive' -Method POST -Headers @{'Content-Type'='application/json'} -Body '{\"title\":\"Critical: Medication Interaction\",\"message\":\"WARNING: Patient has potential medication interaction. Current medications: Warfarin + Aspirin. Verify prescription before dispensing.\",\"type\":\"WARNING\",\"source\":\"OPENMRS\",\"recipientId\":\"doctor-001\"}' | ConvertTo-Json"
echo.
echo.

REM Bed Availability
echo 7. Sending Bed Availability Notification...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications/receive' -Method POST -Headers @{'Content-Type'='application/json'} -Body '{\"title\":\"Bed Available\",\"message\":\"Hospital bed now available in Ward A, Room 304. Patient can be admitted.\",\"type\":\"INFO\",\"source\":\"OPENMRS\",\"recipientId\":\"nurse-005\"}' | ConvertTo-Json"
echo.
echo.

REM Discharge Notice
echo 8. Sending Discharge Notice...
powershell -Command "Invoke-RestMethod -Uri '%BASE_URL%/api/notifications/receive' -Method POST -Headers @{'Content-Type'='application/json'} -Body '{\"title\":\"Patient Discharge\",\"message\":\"Patient Robert Brown (ID: P-54321) cleared for discharge. Follow-up appointment scheduled for next week.\",\"type\":\"INFO\",\"source\":\"OPENMRS\",\"recipientId\":\"doctor-002\"}' | ConvertTo-Json"
echo.
echo.

echo ==========================================
echo All test notifications have been sent!
echo ==========================================
echo.
echo Next steps:
echo 1. Check RabbitMQ Management: http://localhost:15672 (guest/guest)
echo 2. Monitor application logs: docker-compose logs -f app
echo 3. Check PostgreSQL for stored notifications
echo.
pause
