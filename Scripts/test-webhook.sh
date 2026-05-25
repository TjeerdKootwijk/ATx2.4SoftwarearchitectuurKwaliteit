#!/bin/bash

# Webhook Test Script - Test appointment events with HMAC-SHA256 signatures
# This script demonstrates how OpenMRS would send appointment change events to the communication module

BASE_URL="http://localhost:8080"
TENANT_ID="tenant-001"
WEBHOOK_SECRET="webhook-secret-001-for-hmac"

echo "=========================================="
echo "ATx2.4 Webhook Test - Appointment Events"
echo "=========================================="
echo ""

# Function to compute HMAC-SHA256
compute_hmac() {
    echo -n "$1" | openssl dgst -sha256 -hmac "$2" | awk '{print $NF}'
}

# Test 1: New Appointment Event
echo "1. Testing: New Appointment Created"
echo "===================================="
APPOINTMENT_PAYLOAD='{
  "eventId": "evt-001-' $(date +%s) '",
  "appointmentId": "apt-12345",
  "appointmentUuid": "550e8400-e29b-41d4-a716-446655440000",
  "patientId": "P-98765",
  "patientName": "John Doe",
  "appointmentDateTime": "2026-05-10T14:30:00",
  "status": "SCHEDULED",
  "changeType": "CREATED",
  "providerId": "doc-001",
  "providerName": "Dr. Smith",
  "location": "Clinic A",
  "eventOccurredAt": "2026-05-08T10:00:00"
}'

EVENT_ID="evt-001-$(date +%s)"
HMAC_SIGNATURE=$(compute_hmac "$APPOINTMENT_PAYLOAD" "$WEBHOOK_SECRET")

echo "Payload:"
echo "$APPOINTMENT_PAYLOAD" | jq .
echo ""
echo "Sending to: POST $BASE_URL/api/events/appointment"
echo "With headers:"
echo "  X-Tenant-Id: $TENANT_ID"
echo "  X-Event-Id: $EVENT_ID"
echo "  X-HMAC-SHA256: $HMAC_SIGNATURE"
echo ""

curl -X POST "$BASE_URL/api/events/appointment" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Event-Id: $EVENT_ID" \
  -H "X-HMAC-SHA256: $HMAC_SIGNATURE" \
  -d "$APPOINTMENT_PAYLOAD" | jq .

echo ""
echo ""

# Test 2: Updated Appointment Event
echo "2. Testing: Appointment Updated"
echo "================================"
APPOINTMENT_PAYLOAD_2='{
  "eventId": "evt-002-' $(date +%s) '",
  "appointmentId": "apt-12345",
  "appointmentUuid": "550e8400-e29b-41d4-a716-446655440000",
  "patientId": "P-98765",
  "patientName": "John Doe",
  "appointmentDateTime": "2026-05-11T15:00:00",
  "status": "SCHEDULED",
  "changeType": "UPDATED",
  "providerId": "doc-001",
  "providerName": "Dr. Smith",
  "location": "Clinic B",
  "eventOccurredAt": "2026-05-08T10:15:00"
}'

EVENT_ID_2="evt-002-$(date +%s)"
HMAC_SIGNATURE_2=$(compute_hmac "$APPOINTMENT_PAYLOAD_2" "$WEBHOOK_SECRET")

curl -X POST "$BASE_URL/api/events/appointment" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Event-Id: $EVENT_ID_2" \
  -H "X-HMAC-SHA256: $HMAC_SIGNATURE_2" \
  -d "$APPOINTMENT_PAYLOAD_2" | jq .

echo ""
echo ""

# Test 3: Cancelled Appointment Event
echo "3. Testing: Appointment Cancelled"
echo "=================================="
APPOINTMENT_PAYLOAD_3='{
  "eventId": "evt-003-' $(date +%s) '",
  "appointmentId": "apt-12345",
  "appointmentUuid": "550e8400-e29b-41d4-a716-446655440000",
  "patientId": "P-98765",
  "patientName": "John Doe",
  "appointmentDateTime": "2026-05-11T15:00:00",
  "status": "CANCELLED",
  "changeType": "DELETED",
  "providerId": "doc-001",
  "providerName": "Dr. Smith",
  "location": "Clinic B",
  "eventOccurredAt": "2026-05-08T10:20:00"
}'

EVENT_ID_3="evt-003-$(date +%s)"
HMAC_SIGNATURE_3=$(compute_hmac "$APPOINTMENT_PAYLOAD_3" "$WEBHOOK_SECRET")

curl -X POST "$BASE_URL/api/events/appointment" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Event-Id: $EVENT_ID_3" \
  -H "X-HMAC-SHA256: $HMAC_SIGNATURE_3" \
  -d "$APPOINTMENT_PAYLOAD_3" | jq .

echo ""
echo ""

# Test 4: Test with invalid HMAC (should fail)
echo "4. Testing: Invalid HMAC Signature (Should FAIL)"
echo "================================================="
INVALID_HMAC="invalid-hmac-signature-1234567890abcdef"

curl -X POST "$BASE_URL/api/events/appointment" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Event-Id: evt-invalid" \
  -H "X-HMAC-SHA256: $INVALID_HMAC" \
  -d "$APPOINTMENT_PAYLOAD" | jq .

echo ""
echo ""

# Test 5: Test webhook health
echo "5. Testing: Webhook Health Check"
echo "=================================="
curl -X GET "$BASE_URL/api/events/health" | jq .

echo ""
echo "=========================================="
echo "Webhook tests completed!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Check RabbitMQ Management: http://localhost:15672 (guest/guest)"
echo "2. View 'appointment.events' queue for message count"
echo "3. Monitor logs: docker-compose logs -f app"
echo "4. Verify notifications were scheduled"
echo ""
