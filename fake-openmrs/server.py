#!/usr/bin/env python3
"""
Fake OpenMRS REST server voor pipeline-tests.

Simuleert GET /ws/rest/v1/appointments en geeft configureerbare
nep-afspraken terug zodat de volledige FHIR-pipeline getest kan worden
zonder een echte OpenMRS instantie.

Env vars:
  FAKE_OPENMRS_PORT            Poort waarop de server draait           (default: 8090)
  FAKE_APPOINTMENT_COUNT       Aantal nep-afspraken                    (default: 3)
  FAKE_APPOINTMENT_HOURS       Uur vanaf nu voor eerste afspraak       (default: 2)
  FAKE_PATIENT_NAME            Basisnaam van de nep-patiënt            (default: Test Patient)
  FAKE_APPOINTMENT_LOCATION    Locatie in de afspraak                  (default: Polikliniek Kamer 3B)
  FAKE_APPOINTMENT_TYPE        Type afspraak                           (default: Consultatie)
  FAKE_APPOINTMENT_STATUS      OpenMRS status                          (default: Scheduled)
  FAKE_APPOINTMENT_COMMENTS    Instructies voor de patiënt             (default: Nuchter komen)
"""

import json
import os
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT          = int(os.environ.get("FAKE_OPENMRS_PORT", "8090"))
COUNT         = int(os.environ.get("FAKE_APPOINTMENT_COUNT", "3"))
HOURS         = int(os.environ.get("FAKE_APPOINTMENT_HOURS", "2"))
PATIENT_NAME  = os.environ.get("FAKE_PATIENT_NAME", "Test Patient")
LOCATION      = os.environ.get("FAKE_APPOINTMENT_LOCATION", "Polikliniek Kamer 3B")
APPT_TYPE     = os.environ.get("FAKE_APPOINTMENT_TYPE", "Consultatie")
STATUS        = os.environ.get("FAKE_APPOINTMENT_STATUS", "Scheduled")
COMMENTS      = os.environ.get("FAKE_APPOINTMENT_COMMENTS", "Nuchter komen")


def build_appointments():
    """Genereer nep-afspraken relatief aan de huidige tijd."""
    results = []
    for i in range(COUNT):
        # Spreidt afspraken over meerdere dagen
        start = datetime.now(timezone.utc) + timedelta(hours=HOURS + i * 24)
        end   = start + timedelta(minutes=30)

        results.append({
            "uuid": f"fake-appointment-{i + 1:03d}",
            "patient": {
                "uuid": f"fake-patient-{i + 1:03d}",
                "display": f"{PATIENT_NAME} {i + 1}"
            },
            "appointmentType": {
                "display": APPT_TYPE
            },
            "status": STATUS,
            "startDateTime": int(start.timestamp() * 1000),
            "endDateTime":   int(end.timestamp() * 1000),
            "location": {
                "display": LOCATION
            },
            "comments": COMMENTS
        })
    return results


class OpenMrsHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path.startswith("/ws/rest/v1/appointments"):
            body = json.dumps({"results": build_appointments()}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            print(f"[fake-openmrs] Returned {COUNT} appointment(s) to {self.client_address[0]}")
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, fmt, *args):
        # Onderdrukt de standaard Apache-stijl log, wij loggen zelf
        pass


if __name__ == "__main__":
    print(f"[fake-openmrs] Starting on port {PORT}")
    print(f"[fake-openmrs]   Appointments : {COUNT}")
    print(f"[fake-openmrs]   Patient      : {PATIENT_NAME}")
    print(f"[fake-openmrs]   Hours offset : {HOURS}h from now")
    print(f"[fake-openmrs]   Status       : {STATUS}")
    print(f"[fake-openmrs]   Location     : {LOCATION}")

    server = HTTPServer(("0.0.0.0", PORT), OpenMrsHandler)
    server.serve_forever()
