# -*- coding: utf-8 -*-
"""
OpenMRS - Patient en Appointment aanmaken voor Communication Module
===================================================================
Gebruik:
    python -m pip install requests
    python create_patient_and_appointment.py

Pas de CONFIG sectie onderaan aan voor jouw omgeving.
"""

import requests
import json
import sys
from datetime import datetime, timedelta, timezone

# Forceer UTF-8 output zodat speciale tekens goed werken op Windows
if sys.stdout.encoding != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

# ── Configuratie ──────────────────────────────────────────────────────────────

CONFIG = {
    "base_url":  "http://localhost",
    "username":  "admin",
    "password":  "Admin123",
}

# ── Sessie setup ──────────────────────────────────────────────────────────────

session = requests.Session()
session.auth = (CONFIG["username"], CONFIG["password"])
session.headers.update({"Content-Type": "application/json", "Accept": "application/json"})

BASE      = CONFIG["base_url"]
FHIR_BASE = f"{BASE}/openmrs/ws/fhir2/R4"
REST_BASE = f"{BASE}/openmrs/ws/rest/v1"


def log(label: str, data):
    print(f"\n{'─'*60}")
    print(f"  {label}")
    print(f"{'─'*60}")
    if isinstance(data, dict):
        print(json.dumps(data, indent=2, ensure_ascii=False))
    else:
        print(data)


def check(response: requests.Response, verwacht: int = 200):
    if response.status_code not in (verwacht, 200, 201):
        print(f"  [FOUT] HTTP {response.status_code}: {response.text[:300]}")
        response.raise_for_status()
    print(f"  [OK] HTTP {response.status_code}")
    return response.json()


# ── Stap 0: Login testen ──────────────────────────────────────────────────────

def login():
    print("\nStap 0: Verbinding testen...")
    r = session.get(f"{REST_BASE}/session")
    data = check(r)
    if not data.get("authenticated"):
        raise RuntimeError("Login mislukt -- controleer gebruikersnaam/wachtwoord in CONFIG")
    print(f"  Ingelogd als: {data['user']['display']}")


# ── Stap 1: Vereiste UUIDs ophalen ───────────────────────────────────────────

def haal_locatie_uuid() -> str:
    print("\nStap 1a: Locatie ophalen...")
    r = session.get(f"{REST_BASE}/location", params={"v": "default", "limit": 50})
    data = check(r)
    results = data.get("results", [])
    if not results:
        raise RuntimeError("Geen locaties gevonden in OpenMRS")
    print("  Beschikbare locaties:")
    for loc in results:
        print(f"    {loc['display']} -> {loc['uuid']}")
    uuid = results[0]["uuid"]
    print(f"  Gekozen: {results[0]['display']} ({uuid})")
    return uuid


def haal_identifier_type_uuid() -> str:
    print("\nStap 1b: Identifier type ophalen...")
    r = session.get(f"{REST_BASE}/patientidentifiertype", params={"v": "default"})
    data = check(r)
    results = data.get("results", [])
    if not results:
        raise RuntimeError("Geen identifier types gevonden")
    print("  Beschikbare identifier types:")
    for t in results:
        print(f"    {t['display']} -> {t['uuid']}")

    # Voorkeur: types zonder checksum-validatie zodat eigen IDs werken
    voorkeur = ["Old Identification Number", "Legacy ID", "SSN", "ID Card"]
    gekozen = None
    for naam in voorkeur:
        gekozen = next((t for t in results if t["display"] == naam), None)
        if gekozen:
            break
    if not gekozen:
        gekozen = results[0]

    print(f"  Gekozen: {gekozen['display']} ({gekozen['uuid']})")
    return gekozen["uuid"]


def _luhn_mod30_checkdigit(identifier: str) -> str:
    """Berekent het Luhn Mod-30 check digit zoals OpenMRS dat verwacht.
    Gebaseerd op OpenMRS LuhnMod30IdentifierValidator.java:
      - j=0 (rechtsmeest) wordt VERDUBBELD
      - bij doubled >= 30: -= 29  (= MODULUS - 1)
      - check = (30 - total%30) % 30
    """
    CHARSET = "0123456789ACDEFGHJKLMNPRTUVWXY"
    MODULUS = 30
    cleaned = "".join(c for c in identifier.upper() if c in CHARSET)
    total = 0
    for j, char in enumerate(reversed(cleaned)):
        digit = CHARSET.index(char)
        if j % 2 == 0:          # rechtse positie (j=0) en elke tweede daarna
            digit *= 2
            if digit >= MODULUS:
                digit -= MODULUS - 1   # = digit - 29
        total += digit
    check_idx = (MODULUS - (total % MODULUS)) % MODULUS
    return CHARSET[check_idx]


def genereer_openmrs_id() -> tuple[str, str]:
    """Genereert een geldig OpenMRS ID met Luhn Mod-30 check digit.
    Geeft terug: (identifier_string, identifier_type_uuid)"""
    import random
    import time

    print("\nStap 1d: OpenMRS ID genereren...")

    # Probeer eerst via idgen REST (haal numeric source ID op)
    r = session.get(f"{REST_BASE}/idgen/identifiersource", params={"v": "full"})
    if r.status_code == 200:
        sources = r.json().get("results", [])
        for source in sources:
            # Probeer het id-veld (numeriek) te vinden voor de form endpoint
            numeric_id = source.get("id")
            if numeric_id:
                r2 = session.get(
                    f"{BASE}/openmrs/module/idgen/generateIdentifier.form",
                    params={"source": numeric_id, "count": 1}
                )
                if r2.status_code == 200:
                    body = r2.json()
                    gegenereerd_id = (
                        body.get("identifier")
                        or (body.get("identifiers") or [None])[0]
                    )
                    if gegenereerd_id:
                        openmrs_id_type_uuid = "05a29f94-c0ed-11e2-94be-8c13b969e334"
                        print(f"  Gegenereerd via idgen: {gegenereerd_id}")
                        return gegenereerd_id, openmrs_id_type_uuid

    # Fallback: genereer lokaal een geldig Luhn Mod-30 identifier
    charset = "0123456789ACDEFGHJKLMNPRTUVWXY"
    # Gebruik timestamp + random voor uniciteit
    seed = str(int(time.time() * 1000))[-6:] + str(random.randint(10, 99))
    base_id = "".join(
        charset[int(c)] if c.isdigit() and int(c) < len(charset) else c
        for c in seed
    )
    check = _luhn_mod30_checkdigit(base_id)
    gegenereerd_id = base_id + check

    openmrs_id_type_uuid = "05a29f94-c0ed-11e2-94be-8c13b969e334"
    print(f"  Gegenereerd lokaal (Luhn Mod-30): {gegenereerd_id}")
    return gegenereerd_id, openmrs_id_type_uuid


def haal_service_uuid() -> str:
    print("\nStap 1c: Appointment service ophalen...")
    r = session.get(f"{REST_BASE}/appointmentService/all/default")
    data = check(r)
    if not isinstance(data, list) or len(data) == 0:
        raise RuntimeError(
            "Geen appointment services gevonden.\n"
            "Maak er een aan via: OpenMRS Admin > Bahmni Appointment > Manage Services"
        )
    print("  Beschikbare appointment services:")
    for s in data:
        print(f"    {s['name']} -> {s['uuid']}")
    uuid = data[0]["uuid"]
    print(f"  Gekozen: {data[0]['name']} ({uuid})")
    return uuid


# ── Stap 2: Patient aanmaken ──────────────────────────────────────────────────

def maak_patient(locatie_uuid: str, identifier_type_uuid: str, patient_data: dict,
                 openmrs_id: str, openmrs_id_type_uuid: str) -> str:
    print("\nStap 3: Patient aanmaken via OpenMRS REST...")

    # Geslacht: FHIR gebruikt 'male'/'female', OpenMRS REST wil 'M'/'F'
    geslacht_map = {"male": "M", "female": "F", "other": "O", "unknown": "U"}
    geslacht = geslacht_map.get(patient_data["gender"].lower(), "U")

    identifiers = [
        # Vereist: het door idgen gegenereerde OpenMRS ID (heeft geldige checksum)
        {
            "identifierType": openmrs_id_type_uuid,
            "identifier":     openmrs_id,
            "location":       locatie_uuid,
            "preferred":      True
        }
    ]

    # Optioneel: voeg ook het eigen Communication Module ID toe
    if identifier_type_uuid != openmrs_id_type_uuid:
        identifiers.append({
            "identifierType": identifier_type_uuid,
            "identifier":     patient_data["identifier"],
            "location":       locatie_uuid,
            "preferred":      False
        })

    # Telefoonnummer attribute toevoegen zodat de communicatiemodule het kan lezen
    attributes = []
    phone = patient_data.get("phone")
    if phone:
        # OpenMRS standaard attribuuttype voor telefoonnummer
        r_attr = session.get(f"{REST_BASE}/personattributetype",
                             params={"v": "default", "q": "Telephone"})
        if r_attr.status_code == 200:
            attr_results = r_attr.json().get("results", [])
            if attr_results:
                attributes.append({
                    "attributeType": attr_results[0]["uuid"],
                    "value": phone
                })
                print(f"  Telefoonnummer attribuuttype gevonden: {attr_results[0]['display']}")
            else:
                print("  [WAARSCHUWING] Geen 'Telephone' attribuuttype gevonden in OpenMRS")
        else:
            print(f"  [WAARSCHUWING] Kon attribuuttypes niet ophalen: HTTP {r_attr.status_code}")

    payload = {
        "person": {
            "names": [
                {
                    "givenName":  patient_data["givenName"],
                    "familyName": patient_data["familyName"],
                    "preferred":  True
                }
            ],
            "gender":     geslacht,
            "birthdate":  patient_data["birthDate"],
            "addresses":  [],
            "attributes": attributes
        },
        "identifiers": identifiers
    }

    r = session.post(f"{REST_BASE}/patient", json=payload)
    data = check(r, verwacht=201)

    patient_uuid = data["uuid"]
    log("Patient aangemaakt", {
        "uuid":           patient_uuid,
        "naam":           f"{patient_data['givenName']} {patient_data['familyName']}",
        "openmrs_id":     openmrs_id,
        "comm_module_id": patient_data["identifier"]
    })
    return patient_uuid


# ── Stap 3: Appointment aanmaken (Bahmni REST) ────────────────────────────────

def maak_appointment(patient_uuid: str, service_uuid: str, locatie_uuid: str,
                     start: datetime, duur_minuten: int = 30) -> str:
    print("\nStap 3: Appointment aanmaken via Bahmni REST...")

    eind = start + timedelta(minutes=duur_minuten)

    payload = {
        "patientUuid":     patient_uuid,
        "serviceUuid":     service_uuid,
        "startDateTime":   start.isoformat(),
        "endDateTime":     eind.isoformat(),
        "locationUuid":    locatie_uuid,
        "appointmentKind": "Scheduled",
        "status":          "Scheduled",
        "comments":        "Aangemaakt via Communication Module script"
    }

    r = session.post(f"{REST_BASE}/appointments", json=payload)
    data = check(r, verwacht=200)

    appointment_uuid = data["uuid"]
    log("Appointment aangemaakt", {
        "uuid":    appointment_uuid,
        "patient": patient_uuid,
        "start":   start.isoformat(),
        "eind":    eind.isoformat(),
        "status":  data.get("status")
    })
    return appointment_uuid


# ── Stap 4: Verificatie ───────────────────────────────────────────────────────

def verifieer(patient_uuid: str, appointment_uuid: str):
    print("\nStap 4: Verificatie...")

    headers = {"Accept": "application/fhir+json"}
    r = session.get(f"{FHIR_BASE}/Patient/{patient_uuid}", headers=headers)
    p = check(r)
    naam = p["name"][0]["family"] if p.get("name") else "?"
    print(f"  [OK] Patient gevonden: {naam} ({patient_uuid})")

    r = session.get(f"{REST_BASE}/appointments/{appointment_uuid}")
    a = check(r)
    print(f"  [OK] Appointment gevonden: {a.get('startDateTime')} -- status: {a.get('status')}")


# ── Testscenarios voor de Communicatiemodule ─────────────────────────────────
#
# De communicatiemodule stuurt notificaties:
#   - 24 uur voor de afspraak
#   - 1 uur voor de afspraak
#
# Kies een TEST_SCENARIO dat past bij wat je wilt testen:
#
#   "nu_plus_25u"  → afspraak over 25 uur  → comm-module triggert de 24u-notificatie
#   "nu_plus_2u"   → afspraak over 2 uur   → comm-module triggert de 1u-notificatie
#   "nu_plus_30m"  → afspraak over 30 min  → al te laat voor notificaties (edge case)
#   "vandaag"      → afspraak vandaag 17:00 → zichtbaar in OpenMRS UI (vandaag-filter)
#   "custom"       → gebruik CUSTOM_START hieronder

TEST_SCENARIO = "nu_plus_25u"
CUSTOM_START  = datetime(2026, 6, 1, 9, 0, 0, tzinfo=timezone.utc)  # alleen bij "custom"

# Patienten
AUTO_IDENTIFIER = True   # True = uniek timestamp-ID, False = gebruik COMM-001

PATIENTEN = [
    {
        "identifier": "COMM-001",
        "givenName":  "Jan",
        "familyName": "De Vries",
        "gender":     "male",
        "birthDate":  "1985-03-15",
        "phone":      "+31612345678",
        "email":      "jan.devries@example.com",
    },
    {
        "identifier": "COMM-002",
        "givenName":  "Maria",
        "familyName": "Jansen",
        "gender":     "female",
        "birthDate":  "1990-07-22",
        "phone":      "+31698765432",
        "email":      "maria.jansen@example.com",
    },
]


def bereken_start(scenario: str) -> datetime:
    nu = datetime.now(tz=timezone.utc)
    scenarios = {
        "nu_plus_25u": nu + timedelta(hours=25),
        "nu_plus_2u":  nu + timedelta(hours=2),
        "nu_plus_30m": nu + timedelta(minutes=30),
        "vandaag":     nu.replace(hour=17, minute=0, second=0, microsecond=0),
        "custom":      CUSTOM_START,
    }
    if scenario not in scenarios:
        raise ValueError(f"Onbekend scenario: {scenario}. Kies uit: {list(scenarios)}")
    return scenarios[scenario]


# ── Hoofdprogramma ────────────────────────────────────────────────────────────

if __name__ == "__main__":
    try:
        login()

        locatie_uuid                     = haal_locatie_uuid()
        identifier_type_uuid             = haal_identifier_type_uuid()
        service_uuid                     = haal_service_uuid()

        appointment_start = bereken_start(TEST_SCENARIO)
        print(f"\nTest scenario : {TEST_SCENARIO}")
        print(f"Afspraak tijd : {appointment_start.strftime('%Y-%m-%d %H:%M UTC')}")
        print(f"Nu            : {datetime.now(tz=timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}")
        delta = appointment_start - datetime.now(tz=timezone.utc)
        uren  = delta.total_seconds() / 3600
        print(f"Over           : {uren:.1f} uur")
        if uren > 24:
            print("  --> Comm-module stuurt 24u-notificatie over ~{:.1f}u".format(uren - 24))
        elif uren > 1:
            print("  --> Comm-module stuurt 1u-notificatie over ~{:.1f}u".format(uren - 1))
        else:
            print("  --> Afspraak is te dichtbij of al geweest, geen notificaties verwacht")

        resultaten = []
        for patient_data in PATIENTEN:
            if AUTO_IDENTIFIER:
                patient_data = dict(patient_data)
                import time as _time; _time.sleep(1)
                patient_data["identifier"] = f"COMM-{int(datetime.now().timestamp())}"

            openmrs_id, openmrs_id_type_uuid = genereer_openmrs_id()
            patient_uuid     = maak_patient(locatie_uuid, identifier_type_uuid, patient_data,
                                            openmrs_id, openmrs_id_type_uuid)
            appointment_uuid = maak_appointment(patient_uuid, service_uuid, locatie_uuid,
                                                appointment_start, duur_minuten=30)
            resultaten.append({
                "naam":             f"{patient_data['givenName']} {patient_data['familyName']}",
                "patient_uuid":     patient_uuid,
                "appointment_uuid": appointment_uuid,
                "comm_module_id":   patient_data["identifier"],
            })

        verifieer(resultaten[0]["patient_uuid"], resultaten[0]["appointment_uuid"])

        print("\n" + "=" * 60)
        print("  KLAAR -- testdata voor je Communicatiemodule:")
        print("=" * 60)
        for r in resultaten:
            print(f"\n  Patient : {r['naam']} ({r['comm_module_id']})")
            print(f"  patient_uuid     = {r['patient_uuid']}")
            print(f"  appointment_uuid = {r['appointment_uuid']}")
        print()
        print("  Poll endpoint voor de communicatiemodule:")
        start_str = appointment_start.strftime("%Y-%m-%dT%H:%M:%S.000Z")
        eind_str  = (appointment_start + timedelta(hours=26)).strftime("%Y-%m-%dT%H:%M:%S.000Z")
        print(f"  POST /openmrs/ws/rest/v1/appointments/search")
        print(f"  Body: {{\"startDate\": \"{start_str}\", \"endDate\": \"{eind_str}\"}}")
        print("=" * 60 + "\n")

    except Exception as e:
        print(f"\n[FOUT] {e}")
        raise
        raise
