FROM python:3.12-slim

WORKDIR /app
COPY create_patient_and_appointment.py .

# Pas aan als je script dependencies heeft, bv: requests
RUN pip install --no-cache-dir requests

CMD ["python", "create_patient_and_appointment.py"]