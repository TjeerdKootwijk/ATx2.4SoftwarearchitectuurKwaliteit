# ATx2.4 - Communication Module

Event-driven external communication module for OpenMRS appointment notifications.

**Based on:** ADR-1 (SaaS), ADR-2 (Java/Spring), ADR-3 (Webhooks+Polling), ADR-4 (HMAC+Idempotency)

---

## 🚀 Quick Start

### Step 1: Start Services
```bash
cd ATx2.4SoftwarearchitectuurKwaliteit
docker-compose up --build
docker-compose up
```



### Step 2: Monitor
- **Application:** http://localhost:8080 (logs: `docker-compose logs -f app`)
- **MessageProviders:** http://localhost:1337
- **RabbitMQ:** http://localhost:15672 (guest/guest)
- **Webhook Health:** `curl http://localhost:8080/api/events/health`

---

## 📋 What It Does

1. **Receives** HMAC-signed appointment events from OpenMRS
2. **Validates** signature and checks for duplicates (idempotent)
3. **Publishes** to RabbitMQ asynchronously (returns 202 Accepted immediately)
4. **Processes** appointment changes (created, updated, deleted)
5. **Schedules** notifications: 24 hours + 1 hour before appointment
6. **Polls** OpenMRS REST API every 5 minutes as fallback

---

## 🔌 Webhook Endpoint

```
POST /api/events/appointment

Headers:
  X-Tenant-Id: tenant-001
  X-Event-Id: evt-001-unique-id
  X-HMAC-SHA256: <signature>
  Content-Type: application/json

Body:
{
  "appointmentId": "apt-12345",
  "appointmentUuid": "550e8400-e29b-41d4-a716-446655440000",
  "patientId": "P-98765",
  "patientName": "John Doe",
  "appointmentDateTime": "2026-05-10T14:30:00",
  "status": "SCHEDULED",
  "changeType": "CREATED|UPDATED|DELETED|RESCHEDULED",
  "providerId": "doc-001",
  "providerName": "Dr. Smith",
  "location": "Clinic A",
  "eventOccurredAt": "2026-05-08T10:00:00"
}
```

**Response:** `202 Accepted`
```json
{
  "status": "ACCEPTED",
  "message": "Event accepted for processing",
  "timestamp": 1715155200000
}
```

---

## 🔐 HMAC-SHA256 Validation

**Every webhook must include a valid HMAC signature.**

**Pre-configured Tenants:**
- `tenant-001` → Secret: `webhook-secret-001-for-hmac`
- `tenant-002` → Secret: `webhook-secret-002-for-hmac`

**Generate Signature (bash):**
```bash
PAYLOAD='{"appointmentId":"apt-12345",...}'
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "webhook-secret-001-for-hmac" | awk '{print $NF}')
```

**Generate Signature (PowerShell):**
```powershell
$payload = '{"appointmentId":"apt-12345",...}'
$secret = 'webhook-secret-001-for-hmac'
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
$signature = -join ($hash | ForEach-Object { $_.ToString('x2') })
```

---

## 🏗️ Architecture

```
OpenMRS (multiple tenants)
    ↓ HTTPS + HMAC
WebhookController
    ↓ Validate + Idempotency check
RabbitMQ (appointment.events)
    ↓
AppointmentEventListener
    ↓
NotificationScheduler
    ↓
SMS/Email/Push (24h + 1h before)

+ PollingJob (every 5 min fallback)
```

---

## 📁 Project Structure

```
src/main/java/com/example/atx24softwarearchitectuurkwaliteit/
├── Application.java
├── controller/
│   └── WebhookController.java ......... POST /api/events/appointment
├── service/
│   ├── IdempotencyService.java ........ HMAC validation + dedup
│   ├── TenantService.java ............ Multitenancy management
│   ├── PollingJob.java .............. REST API fallback polling
│   └── NotificationScheduler.java .... Scheduled notifications
├── listener/
│   └── AppointmentEventListener.java .. RabbitMQ consumer
├── model/
│   ├── AppointmentChangedEvent.java ... Event model
│   └── TenantConfiguration.java ...... Tenant config
├── config/
│   └── RabbitMQConfig.java ........... Message broker setup
└── resources/
    └── application.properties
```

---

## 🔧 Key Services

### IdempotencyService
- Validates HMAC-SHA256 signature
- Tracks processed events (prevents duplicates)
- Constant-time comparison (timing attack prevention)

### TenantService
- Manages multiple OpenMRS organizations
- Per-tenant configuration and secrets
- Validates tenant is active

### PollingJob
- Runs every 5 minutes
- Polls OpenMRS REST API
- Catches missed webhooks
- Uses `lastPolledAt` per tenant for efficiency

### AppointmentEventListener
- Consumes from RabbitMQ queue
- Handles: CREATED, UPDATED, DELETED, RESCHEDULED
- Schedules notifications

### NotificationScheduler
- Runs every 1 minute
- Sends notifications at scheduled times
- Ready for SMS/Email/Push integration

---

## 🧪 Testing

### Option 1: Automatic Tests (Recommended)

**Windows:**
```bash
test-webhook.bat
```

**Linux/Mac:**
```bash
chmod +x test-webhook.sh
./test-webhook.sh
```

### Option 2: Manual Testing

```bash
PAYLOAD='{"appointmentId":"apt-12345","patientName":"John Doe","appointmentDateTime":"2026-05-10T14:30:00","status":"SCHEDULED","changeType":"CREATED","providerId":"doc-001","providerName":"Dr. Smith","location":"Clinic A","eventOccurredAt":"2026-05-08T10:00:00"}'

SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "webhook-secret-001-for-hmac" | awk '{print $NF}')

curl -X POST http://localhost:8080/api/events/appointment \
  -H "X-Tenant-Id: tenant-001" \
  -H "X-Event-Id: evt-001-$(date +%s)" \
  -H "X-HMAC-SHA256: $SIGNATURE" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"
```

---

## 📊 Monitoring

### RabbitMQ Console
- **URL:** http://localhost:15672
- **Username:** guest
- **Password:** guest
- **Check:** `appointment.events` queue

### Application Logs
```bash
docker-compose logs -f app
```

**Look for:**
```
Received appointment event for tenant
Event marked as processed
Appointment event published to RabbitMQ
Checking for notifications to send
Starting appointment polling job
```

### Health Check
```bash
curl http://localhost:8080/api/events/health
```

---

## 🔨 Common Commands

| Command | Purpose |
|---------|---------|
| `docker-compose up --build` | Start all services |
| `docker-compose down` | Stop services |
| `docker-compose down -v` | Stop and remove data |
| `docker-compose ps` | Check service status |
| `docker-compose logs -f app` | View app logs |
| `test-webhook.bat` | Send test events (Windows) |
| `./test-webhook.sh` | Send test events (Linux/Mac) |

---

## ⚙️ Configuration

**application.properties:**
```properties
# RabbitMQ
spring.rabbitmq.host=rabbitmq
spring.rabbitmq.port=5672

# Scheduling
spring.task.scheduling.pool.size=2

# Polling
app.polling.enabled=true
app.polling.interval-seconds=300

# Multitenancy
app.multitenancy.enabled=true
```

---

## 🔒 Security

✅ **Implemented:**
- HMAC-SHA256 signature validation
- Per-tenant webhook secrets
- Idempotent processing (no duplicates)
- Constant-time comparison

⚠️ **Production Checklist:**
- [ ] Use Vault for secrets management
- [ ] Enable HTTPS/TLS
- [ ] Add API authentication
- [ ] Implement rate limiting
- [ ] Enable audit logging
- [ ] Set IP whitelisting per tenant

---

## 🐛 Troubleshooting

### Webhook Returns 401 Unauthorized
```bash
# Check tenant exists
docker-compose logs app | grep "Invalid tenant"

# Check HMAC signature
docker-compose logs app | grep "Invalid HMAC"
```

### Events Not in Queue
```bash
# Check RabbitMQ has messages
# Visit: http://localhost:15672

# View listener logs
docker-compose logs app | grep "Received appointment"
```

### Polling Not Running
```bash
docker-compose logs app | grep "polling"
```

### Services Won't Start
```bash
docker-compose logs
```

---

## 📚 More Information

- **[QUICKSTART.md](./QUICKSTART.md)** - Step-by-step getting started
- **[ADR_IMPLEMENTATION.md](./ADR_IMPLEMENTATION.md)** - Refactoring details
- **[ADR/](./ADR/)** - Architectural Decision Records
  - ADR-1: Standalone SaaS service
  - ADR-2: Technology stack
  - ADR-3: Webhooks + polling
  - ADR-4: HMAC + idempotency

---

## 🎯 Next Steps

1. ✅ Start services: `docker-compose up --build`
2. ✅ Test webhook: `test-webhook.bat` or `test-webhook.sh`
3. ⬜ Configure actual OpenMRS
4. ⬜ Register new tenant with real credentials
5. ⬜ Set up notification providers (SMS/Email)
6. ⬜ Deploy to production

---

## 📞 Support

- Review [ADR-4](./ADR/ADR-4.md) for webhook details
- Check application logs for errors
- Verify HMAC signature calculation
- Monitor RabbitMQ queue

---

**Status:** ✅ Ready for OpenMRS Integration
