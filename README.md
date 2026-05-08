# ATx2.4 Softwarearchitectuur Kwaliteit - Communication Module

An event-driven external communication module designed to process OpenMRS appointment notifications through a message-driven architecture with multi-tenant support.

## Overview

This module serves as an external communication gateway that:
- **Receives appointment events** from OpenMRS via secure webhooks (HMAC-SHA256)
- **Processes asynchronously** using RabbitMQ message broker
- **Supports multiple organizations** with complete tenant isolation
- **Ensures reliability** through idempotent event processing and polling fallback
- **Scales horizontally** with containerized deployment
- **Sends notifications** at scheduled times (24h and 1h before appointments)

## Architecture

### Based on Architectural Decision Records (ADRs)

- **ADR-1**: Standalone SaaS service (not embedded in OpenMRS) - enables multitenancy
- **ADR-2**: Java + Spring Boot + RabbitMQ + PostgreSQL stack
- **ADR-3**: Webhooks + REST API polling fallback for robustness
- **ADR-4**: Asynchronous processing with idempotency and per-tenant tracking

### Component Overview

```
┌─────────────────┐
│  OpenMRS        │
│  (Multiple      │
│   Tenants)      │
└────────┬────────┘
         │ HTTPS POST
         │ + HMAC-SHA256
         ▼
┌──────────────────────────────────────────┐
│  WebhookController                       │
│  - Validates HMAC signature              │
│  - Checks idempotency (eventId)          │
│  - Returns 202 Accepted immediately      │
└──────────┬───────────────────────────────┘
           │ Publishes (if new)
           ▼
┌──────────────────────────────────────────┐
│  RabbitMQ                                │
│  Exchange: appointment.events            │
│  Queue: appointment.events.queue         │
└──────────┬───────────────────────────────┘
           │
        ┌──┴──┐
        │     │
        ▼     ▼
   ┌────────────────┐    ┌──────────────────┐
   │ Event          │    │ PollingJob       │
   │ Listener       │    │ (Fallback)       │
   │ (Webhook)      │    │ - Runs every 5m  │
   │                │    │ - Polls REST API │
   │                │    │ - Fills gaps      │
   └────────┬───────┘    └──────┬───────────┘
            │                   │
            └───────┬───────────┘
                    │
                    ▼
         ┌─────────────────────┐
         │ Appointment Event   │
         │ Listener            │
         │ - Schedules         │
         │   notifications     │
         └──────────┬──────────┘
                    │
                    ▼
         ┌─────────────────────┐
         │ Notification        │
         │ Scheduler           │
         │ - Sends at 24h      │
         │ - Sends at 1h       │
         └─────────────────────┘
```

## Project Structure

```
src/
├── main/
│   ├── java/com/example/atx24softwarearchitectuurkwaliteit/
│   │   ├── Application.java                      # Entry point + tenant initialization
│   │   ├── controller/
│   │   │   └── WebhookController.java            # Webhook event receiver (HMAC validation)
│   │   ├── service/
│   │   │   ├── IdempotencyService.java           # HMAC validation + event deduplication
│   │   │   ├── TenantService.java                # Multitenancy management
│   │   │   ├── PollingJob.java                   # REST API polling fallback
│   │   │   └── NotificationScheduler.java        # Scheduled notification sending
│   │   ├── listener/
│   │   │   └── AppointmentEventListener.java     # RabbitMQ consumer
│   │   ├── model/
│   │   │   ├── AppointmentChangedEvent.java      # Domain event model
│   │   │   └── TenantConfiguration.java          # Tenant config
│   │   ├── dto/
│   │   │   └── AppointmentEventDto.java          # Webhook DTO
│   │   └── config/
│   │       └── RabbitMQConfig.java               # Message broker setup
│   └── resources/
│       └── application.properties
└── test/
    └── java/...
```

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Gradle (for local development)

### Quick Start

1. **Start the services:**
```bash
cd ATx2.4SoftwarearchitectuurKwaliteit
docker-compose up --build
```

2. **Verify services are running:**
```bash
docker-compose ps
```

3. **Test webhook:**
```bash
# Linux/Mac
chmod +x test-webhook.sh
./test-webhook.sh

# Windows
test-webhook.bat
```

4. **Monitor processing:**
```bash
# Watch application logs
docker-compose logs -f app

# Check RabbitMQ
http://localhost:15672  (guest/guest)
```

## API Documentation

### Webhook Endpoint: Receive Appointment Events

```
POST /api/events/appointment

Headers:
  Content-Type: application/json
  X-Tenant-Id: <tenant-identifier>
  X-Event-Id: <unique-event-id>
  X-HMAC-SHA256: <hmac-sha256-signature>
```

**Request Body (fat event with all required data):**
```json
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

**Success Response (202 Accepted):**
```json
{
  "status": "ACCEPTED",
  "message": "Event accepted for processing",
  "timestamp": 1715155200000
}
```

**Error Response (401 Unauthorized):**
```json
{
  "status": "INVALID_SIGNATURE",
  "message": "HMAC validation failed",
  "timestamp": 1715155200000
}
```

### HMAC-SHA256 Validation

OpenMRS must sign the webhook payload using the tenant's webhook secret:

```
HMAC-SHA256(payload, webhook_secret)
```

**Example (bash):**
```bash
PAYLOAD='{"appointmentId":"apt-12345",...}'
SECRET='webhook-secret-001-for-hmac'
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}')

curl -X POST http://localhost:8080/api/events/appointment \
  -H "X-HMAC-SHA256: $SIGNATURE" \
  -H "X-Tenant-Id: tenant-001" \
  -d "$PAYLOAD"
```

**Example (PowerShell):**
```powershell
$payload = '{"appointmentId":"apt-12345",...}'
$secret = 'webhook-secret-001-for-hmac'
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
$signature = -join ($hash | ForEach-Object { $_.ToString('x2') })

Invoke-RestMethod -Uri "http://localhost:8080/api/events/appointment" `
  -Method POST `
  -Headers @{
    "Content-Type" = "application/json"
    "X-Tenant-Id" = "tenant-001"
    "X-Event-Id" = "evt-001-123456"
    "X-HMAC-SHA256" = $signature
  } `
  -Body $payload
```

### Health Endpoint

```
GET /api/events/health
```

**Response:**
```json
{
  "status": "UP",
  "message": "Webhook service is running"
}
```

## Event Processing Flow

### 1. Webhook Reception
- OpenMRS sends HMAC-signed event to `/api/events/appointment`
- WebhookController validates signature immediately
- Returns `202 Accepted` without waiting for processing

### 2. Idempotency Check
- Event ID checked against processed events
- If already processed: silently skipped
- If new: marked as processed, published to RabbitMQ

### 3. Asynchronous Processing
- Event published to `appointment.events` RabbitMQ queue
- AppointmentEventListener consumes from queue
- Determines notification schedule:
  - 24 hours before appointment
  - 1 hour before appointment

### 4. Fallback Polling (Every 5 minutes)
- PollingJob queries OpenMRS REST API per tenant
- Uses `since` parameter based on `lastPolledAt` timestamp
- Filters out already-processed events via idempotency check
- Fills gaps if webhooks were missed

### 5. Notification Scheduling
- NotificationScheduler runs every minute
- Checks for appointments needing notifications
- Sends via configured provider (SMS, Email, Push)
- Marks notifications as sent

## Multitenancy

Each tenant has isolated configuration:

```java
TenantConfiguration {
  tenantId: "tenant-001",
  organizationName: "Local OpenMRS",
  openMrsBaseUrl: "http://openmrs:8080/openmrs",
  openMrsUsername: "admin",
  openMrsPassword: "Admin123",
  webhookSecret: "webhook-secret-001-for-hmac",
  notificationProvider: "SMS|EMAIL|PUSH",
  providerApiKey: "...",
  providerSecret: "..."
}
```

### Sample Tenants (Pre-configured)

- **tenant-001**: Local OpenMRS Instance
  - URL: http://openmrs:8080/openmrs
  - Secret: `webhook-secret-001-for-hmac`

- **tenant-002**: Demo Healthcare Organization
  - URL: http://localhost:8080/openmrs
  - Secret: `webhook-secret-002-for-hmac`

## Monitoring

### RabbitMQ Management

- **URL**: http://localhost:15672
- **Username**: guest
- **Password**: guest

Monitor:
- `appointment.events` queue message count
- Message publish/consume rates
- Consumer connections

### Application Logs

```bash
docker-compose logs -f app
```

**Key log patterns:**
```
Received appointment event for tenant
Event marked as processed
Appointment event published to RabbitMQ
Starting appointment polling job
```

### Spring Boot Actuator

- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics

## Configuration

### application.properties

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

### Environment Variables

Override in `.env` file or via `docker-compose`:

```
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
```

## Idempotency & Resilience

### Duplicate Prevention

- **EventId**: Unique identifier for each event
- **processed_events table**: Tracks all processed event IDs (30-day retention)
- **Database constraint**: Prevents duplicate inserts

If event arrives via both webhook and polling:
1. First arrival: processed, published to RabbitMQ
2. Second arrival: idempotency check prevents reprocessing
3. Result: Patient receives single notification

### Failure Scenarios

| Scenario | Handling |
|----------|----------|
| Webhook fails | PollingJob picks up in next 5-min cycle |
| Polling fails | Next cycle tries again |
| Module restarts | In-memory idempotency resets (use DB in prod) |
| OpenMRS down | Module queues events, retries when available |
| RabbitMQ down | Webhook returns error, retry from OpenMRS |

## Deployment

### Docker Compose (Development/Testing)

```bash
docker-compose up --build
```

### Kubernetes (Production)

For production deployment:
1. Externalize secrets (TenantConfiguration)
2. Use Persistent Volumes for PostgreSQL
3. Configure HPA for PollingJob and Listeners
4. Enable distributed tracing (OpenTelemetry)
5. Setup monitoring (Prometheus/Grafana)

## Testing

### Test Webhook Events

```bash
# Linux/Mac
./test-webhook.sh

# Windows
test-webhook.bat
```

### Manual cURL Testing

```bash
# Generate HMAC
PAYLOAD='{"appointmentId":"apt-12345",...}'
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "webhook-secret-001-for-hmac" | awk '{print $NF}')

# Send
curl -X POST http://localhost:8080/api/events/appointment \
  -H "X-HMAC-SHA256: $SIGNATURE" \
  -H "X-Tenant-Id: tenant-001" \
  -H "X-Event-Id: evt-001-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"
```

## Security Considerations

### Current Implementation (Development)
- ✓ HMAC-SHA256 webhook validation
- ✓ Per-tenant webhook secrets
- ✓ Tenant isolation

### Production Recommendations
1. **Secrets Management**: Use HashiCorp Vault or AWS Secrets Manager
2. **TLS/SSL**: Enforce HTTPS for all endpoints
3. **API Authentication**: Add OAuth2/JWT for management endpoints
4. **Rate Limiting**: Prevent abuse of webhook endpoint
5. **Audit Logging**: Log all tenant activities
6. **IP Whitelisting**: Restrict webhook sources per tenant
7. **Data Encryption**: Encrypt sensitive data at rest
8. **Database**: Use managed database service with backups

## Future Enhancements

- [ ] SMS notification provider integration (SwiftSend, LegacyLink)
- [ ] Email notification provider integration (AsyncFlow, SecurePost)
- [ ] Push notification support
- [ ] Real-time WebSocket notifications
- [ ] Notification preferences per patient
- [ ] Delivery tracking and retry policies
- [ ] Analytics and reporting dashboard
- [ ] Multi-language notification templates
- [ ] Distributed tracing (OpenTelemetry)
- [ ] GraphQL API for complex queries

## Troubleshooting

### Webhook Not Received

1. **Check tenant validity**:
   ```bash
   docker-compose logs app | grep "Invalid tenant"
   ```

2. **Verify HMAC signature**:
   ```bash
   docker-compose logs app | grep "Invalid HMAC"
   ```

3. **Check network connectivity**:
   ```bash
   curl -v http://localhost:8080/api/events/health
   ```

### Events Not Processing

1. **Check RabbitMQ queue**:
   - Visit http://localhost:15672
   - Check `appointment.events` queue message count

2. **View listener logs**:
   ```bash
   docker-compose logs app | grep "Received appointment"
   ```

3. **Verify database connectivity**:
   ```bash
   docker-compose logs postgres
   ```

### Polling Not Running

1. **Check scheduling is enabled**:
   ```properties
   spring.task.scheduling.pool.size=2
   ```

2. **View polling logs**:
   ```bash
   docker-compose logs app | grep "polling"
   ```

3. **Verify OpenMRS connectivity**:
   - Check tenant configuration URLs
   - Verify credentials

## Documentation

- [Architecture Decision Records](./ADR/)
  - ADR-1: Standalone SaaS service
  - ADR-2: Technology stack selection
  - ADR-3: Integration method (webhooks + polling)
  - ADR-4: Webhook implementation with idempotency
- [Docker Setup Guide](./README_DOCKER.md)
- [Quick Start](./QUICKSTART.md)

## Contributing

1. Review ADRs before making changes
2. Add tests for new features
3. Update documentation
4. Follow Spring Boot best practices

## Support

For issues or questions, refer to ADRs and documentation.

