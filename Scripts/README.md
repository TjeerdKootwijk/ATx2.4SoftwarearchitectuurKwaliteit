# Scripts

Deze map bevat een Python script waarmee je handmatig een patient en afspraak aanmaakt in een draaiende OpenMRS-instantie. Dit is handig om te testen of de communicatiemodule de afspraak oppikt en notificaties verstuurt op het juiste moment.

---

## Vereisten

- Python 3.9 of hoger
- De `requests` bibliotheek installeren:

```bash
pip install requests
```

- Een draaiende OpenMRS-instantie (lokaal of via Docker)

---

## create_patient_and_appointment.py

Dit script maakt automatisch een patient aan en plant een afspraak in op een zelf gekozen tijdstip. Daarna kun je controleren of de communicatiemodule de 24u- of 1u-notificatie verstuurt.

---

### Stap 1: Configuratie aanpassen

Open het script en pas de `CONFIG` sectie bovenaan aan zodat die naar jouw OpenMRS-instantie wijst:

```python
CONFIG = {
    "base_url":  "http://localhost",   # URL van jouw OpenMRS-instantie
    "username":  "admin",
    "password":  "Admin123",
}
```

---

### Stap 2: Testscenario kiezen

Verander `TEST_SCENARIO` in het script naar het gewenste scenario:

```python
TEST_SCENARIO = "nu_plus_25u"
```

| Scenario | Afspraak gepland over | Verwacht effect |
|---|---|---|
| `nu_plus_25u` | 25 uur | Communicatiemodule verstuurt de 24u-notificatie |
| `nu_plus_2u` | 2 uur | Communicatiemodule verstuurt de 1u-notificatie |
| `nu_plus_30m` | 30 minuten | Te laat voor notificaties (edge case testen) |
| `vandaag` | Vandaag om 17:00 | Zichtbaar in OpenMRS dagelijks overzicht |
| `custom` | Zelf instellen via `CUSTOM_START` | Vrij te kiezen tijdstip |

---

### Stap 3: Script uitvoeren

Zorg dat je in de root van het project staat en voer het script uit:

**Windows (PowerShell of CMD):**

```powershell
python Scripts\create_patient_and_appointment.py
```

**Linux / Mac:**

```bash
python3 Scripts/create_patient_and_appointment.py
```

---

### Verwachte output

Als het script succesvol is, zie je aan het einde zoiets als:

```
════════════════════════════════════════════════════
  KLAAR -- testdata voor je Communicatiemodule:
════════════════════════════════════════════════════

  Patient : Jan De Vries (COMM-1716630000)
  patient_uuid     = 1a2b3c4d-...
  appointment_uuid = 9f8e7d6c-...

  Poll endpoint voor de communicatiemodule:
  POST /openmrs/ws/rest/v1/appointments/search
  Body: {"startDate": "...", "endDate": "..."}
```

---

## Na het uitvoeren

Controleer in Grafana (http://localhost:3000) of de module de afspraak heeft opgepikt en of er notificaties zijn ingepland of verstuurd. Zie [grafana/README.md](../grafana/README.md) voor uitleg over het dashboard.
