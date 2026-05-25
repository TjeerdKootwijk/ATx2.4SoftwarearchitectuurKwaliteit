# Scripts

Deze map bevat een Python script waarmee je handmatig een patient en afspraak aanmaakt in een draaiende OpenMRS-instantie. Dit is handig om te testen of de communicatiemodule de afspraak oppikt en notificaties verstuurt op het juiste moment.

---

## Vereisten

- Python 3.9 of hoger
- De `requests` bibliotheek:

```bash
pip install requests
```

- Een draaiende OpenMRS-instantie (lokaal of via Docker)

---

## create_patient_and_appointment.py

Dit script maakt automatisch een patient aan en plant een afspraak in op een zelf gekozen tijdstip. Daarna kun je controleren of de communicatiemodule de 24u- of 1u-notificatie verstuurt.

**Configuratie aanpassen**

Open het script en pas de `CONFIG` sectie bovenaan aan:

```python
CONFIG = {
    "base_url":  "http://localhost",   # URL van jouw OpenMRS-instantie
    "username":  "admin",
    "password":  "Admin123",
}
```

**Testscenario kiezen**

Verander `TEST_SCENARIO` naar het gewenste scenario:

| Scenario | Afspraak gepland over | Verwacht effect |
|---|---|---|
| `nu_plus_25u` | 25 uur | Communicatiemodule verstuurt de 24u-notificatie |
| `nu_plus_2u` | 2 uur | Communicatiemodule verstuurt de 1u-notificatie |
| `nu_plus_30m` | 30 minuten | Te laat voor notificaties (edge case testen) |
| `vandaag` | Vandaag om 17:00 | Zichtbaar in OpenMRS dagelijks overzicht |
| `custom` | Zelf instellen via `CUSTOM_START` | Vrij te kiezen tijdstip |

**Script uitvoeren**

```bash
python Scripts/create_patient_and_appointment.py
```

Het script print aan het einde de aangemaakte `patient_uuid` en `appointment_uuid`, en toont het poll-endpoint dat de communicatiemodule gebruikt om de afspraak op te halen.

---

## Na het uitvoeren

Controleer in Grafana (http://localhost:3000) of de module de afspraak heeft opgepikt en of er notificaties zijn ingepland of verstuurd.
