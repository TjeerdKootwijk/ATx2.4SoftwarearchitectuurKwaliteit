#!/usr/bin/env python3
"""
Fake OpenMRS REST server voor pipeline-tests.

Simuleert GET /ws/rest/v1/appointments en geeft configureerbare
nep-afspraken terug zodat de volledige FHIR-pipeline getest kan worden
zonder een echte OpenMRS instantie.

Env vars:
  FAKE_OPENMRS_PORT              Poort waarop de server draait           (default: 8090)
  FAKE_APPOINTMENT_COUNT_1H      Aantal afspraken ~1u in de toekomst     (default: 2)
  FAKE_APPOINTMENT_COUNT_24H     Aantal afspraken ~24u in de toekomst    (default: 2)
  FAKE_APPOINTMENT_HOURS_1H      Uren vanaf nu voor 1h-afspraken         (default: 2)
  FAKE_APPOINTMENT_HOURS_24H     Uren vanaf nu voor 24h-afspraken        (default: 25)
  FAKE_PATIENT_NAME              Basisnaam van de nep-patient             (default: Test Patient)
  FAKE_APPOINTMENT_LOCATION      Locatie in de afspraak                   (default: Polikliniek Kamer 3B)
  FAKE_APPOINTMENT_TYPE          Type afspraak                            (default: Consultatie)
  FAKE_APPOINTMENT_STATUS        OpenMRS status                           (default: Scheduled)
  FAKE_APPOINTMENT_COMMENTS      Instructies voor de patient              (default: Nuchter komen)

  Legacy (backwards compatible):
  FAKE_APPOINTMENT_COUNT         Totaal aantal (wordt gebruikt als COUNT_1H en COUNT_24H niet gezet zijn)
  FAKE_APPOINTMENT_HOURS         Uren offset (wordt gebruikt als HOURS_1H niet gezet is)
"""

import json
import os
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT         = int(os.environ.get("FAKE_OPENMRS_PORT", "8090"))
PATIENT_NAME = os.environ.get("FAKE_PATIENT_NAME",         "Test Patient")
LOCATION     = os.environ.get("FAKE_APPOINTMENT_LOCATION", "Polikliniek Kamer 3B")
APPT_TYPE    = os.environ.get("FAKE_APPOINTMENT_TYPE",     "Consultatie")
STATUS       = os.environ.get("FAKE_APPOINTMENT_STATUS",   "Scheduled")
COMMENTS     = os.environ.get("FAKE_APPOINTMENT_COMMENTS", "Nuchter komen")

# Legacy fallbacks
_legacy_count = int(os.environ.get("FAKE_APPOINTMENT_COUNT", "3"))
_legacy_hours = int(os.environ.get("FAKE_APPOINTMENT_HOURS", "2"))

COUNT_1H  = int(os.environ.get("FAKE_APPOINTMENT_COUNT_1H",  _legacy_count))
COUNT_24H = int(os.environ.get("FAKE_APPOINTMENT_COUNT_24H", 0))
HOURS_1H  = int(os.environ.get("FAKE_APPOINTMENT_HOURS_1H",  _legacy_hours))
HOURS_24H = int(os.environ.get("FAKE_APPOINTMENT_HOURS_24H", 25))


def build_appointments():
    """Genereer nep-afspraken: een groep voor 1h-reminders en een groep voor 24h-reminders."""
    results = []
    index   = 1

    # Groep 1: afspraken voor de 1h-reminder (binnen 1-12 uur)
    for i in range(COUNT_1H):
        start = datetime.now(timezone.utc) + timedelta(hours=HOURS_1H + i)
        end   = start + timedelta(minutes=30)
        results.append(_make_appointment(index, start, end, "1h"))
        index += 1

    # Groep 2: afspraken voor de 24h-reminder (24-48 uur van nu)
    for i in range(COUNT_24H):
        start = datetime.now(timezone.utc) + timedelta(hours=HOURS_24H + i)
        end   = start + timedelta(minutes=30)
        results.append(_make_appointment(index, start, end, "24h"))
        index += 1

    return results


def _make_appointment(index, start, end, group):
    return {
        "uuid": f"fake-appointment-{index:03d}",
        "patient": {
            "uuid":    f"fake-patient-{index:03d}",
            "display": f"{PATIENT_NAME} {index}"
        },
        "appointmentType": {
            "display": APPT_TYPE
        },
        "status":        STATUS,
        "startDateTime": int(start.timestamp() * 1000),
        "endDateTime":   int(end.timestamp()   * 1000),
        "location": {
            "display": f"{LOCATION} ({group}-group)"
        },
        "comments": COMMENTS
    }


class OpenMrsHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path.startswith("/ws/rest/v1/appointments"):
            appointments = build_appointments()
            body = json.dumps({"results": appointments}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            print(f"[fake-openmrs] Returned {len(appointments)} appointment(s) "
                  f"({COUNT_1H} x 1h-group @ +{HOURS_1H}h, "
                  f"{COUNT_24H} x 24h-group @ +{HOURS_24H}h) "
                  f"to {self.client_address[0]}")
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    total = COUNT_1H + COUNT_24H
    print(f"[fake-openmrs] Starting on port {PORT}")
    print(f"[fake-openmrs]   1h-group  : {COUNT_1H} appointment(s) starting in {HOURS_1H}h")
    print(f"[fake-openmrs]   24h-group : {COUNT_24H} appointment(s) starting in {HOURS_24H}h")
    print(f"[fake-openmrs]   Total     : {total} appointment(s)")
    print(f"[fake-openmrs]   Patient   : {PATIENT_NAME}")
    print(f"[fake-openmrs]   Status    : {STATUS}")
    print(f"[fake-openmrs]   Location  : {LOCATION}")

    server = HTTPServer(("0.0.0.0", PORT), OpenMrsHandler)
    server.serve_forever()
