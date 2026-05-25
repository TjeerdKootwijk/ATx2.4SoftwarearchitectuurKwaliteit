#!/bin/bash

# Example Notification Requests for ATx2.4 Communication Module
# Usage: Run from the command line to send test notifications

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "ATx2.4 Communication Module - Test Script"
echo "=========================================="
echo ""

# Check service status
echo "1. Checking service status..."
curl -s -X GET "$BASE_URL/api/notifications/status" | json_pp
echo ""
echo ""

# Patient Alert
echo "2. Sending Patient Alert..."
curl -s -X POST "$BASE_URL/api/notifications/receive" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Patient Vital Signs Alert",
    "message": "Patient John Doe (ID: P-12345) vital signs have changed significantly. Blood pressure: 160/100 mmHg",
    "type": "ALERT",
    "source": "OPENMRS",
    "recipientId": "doctor-001"
  }' | json_pp
echo ""
echo ""

# Lab Result
echo "3. Sending Lab Result Notification..."
curl -s -X POST "$BASE_URL/api/notifications/receive" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Lab Results Available",
    "message": "Blood test results for patient ID P-98765 are ready for review. Hemoglobin: 13.5 g/dL",
    "type": "INFO",
    "source": "OPENMRS",
    "recipientId": "lab-tech-003"
  }' | json_pp
echo ""
echo ""

# Prescription
echo "4. Sending Prescription Notification..."
curl -s -X POST "$BASE_URL/api/notifications" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "New Prescription",
    "message": "Prescription for patient Jane Smith: Amoxicillin 500mg, 3 times daily for 7 days",
    "type": "INFO",
    "source": "OPENMRS",
    "recipientId": "pharmacist-002"
  }' | json_pp
echo ""
echo ""

# Appointment Reminder
echo "5. Sending Appointment Reminder..."
curl -s -X POST "$BASE_URL/api/notifications/receive" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Appointment Reminder",
    "message": "Patient Michael Johnson has a follow-up appointment tomorrow at 2:30 PM with Dr. Smith",
    "type": "INFO",
    "source": "OPENMRS",
    "recipientId": "clinic-staff-001"
  }' | json_pp
echo ""
echo ""

# Critical Warning
echo "6. Sending Critical Warning..."
curl -s -X POST "$BASE_URL/api/notifications/receive" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Critical: Medication Interaction",
    "message": "WARNING: Patient has potential medication interaction. Current medications: Warfarin + Aspirin. Verify prescription before dispensing.",
    "type": "WARNING",
    "source": "OPENMRS",
    "recipientId": "doctor-001"
  }' | json_pp
echo ""
echo ""

# Bed Availability
echo "7. Sending Bed Availability Notification..."
curl -s -X POST "$BASE_URL/api/notifications/receive" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Bed Available",
    "message": "Hospital bed now available in Ward A, Room 304. Patient can be admitted.",
    "type": "INFO",
    "source": "OPENMRS",
    "recipientId": "nurse-005"
  }' | json_pp
echo ""
echo ""

# Discharge Notice
echo "8. Sending Discharge Notice..."
curl -s -X POST "$BASE_URL/api/notifications/receive" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Patient Discharge",
    "message": "Patient Robert Brown (ID: P-54321) cleared for discharge. Follow-up appointment scheduled for next week.",
    "type": "INFO",
    "source": "OPENMRS",
    "recipientId": "doctor-002"
  }' | json_pp
echo ""
echo ""

echo "=========================================="
echo "All test notifications have been sent!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Check RabbitMQ Management: http://localhost:15672 (guest/guest)"
echo "2. Monitor application logs: docker-compose logs -f app"
echo "3. Check PostgreSQL for stored notifications"
echo ""
