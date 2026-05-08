# Docker Setup Guide

This project includes Docker configuration for easy containerization and local development. It provides an external communication module that processes notifications from OpenMRS and other sources via RabbitMQ.

## Architecture Overview

The communication module consists of:
- **Spring Boot Application**: REST API for receiving notifications
- **PostgreSQL Database**: Data persistence
- **RabbitMQ Message Broker**: Asynchronous message processing
- **Health Checks**: Automated service health monitoring

## Files Added

- **Dockerfile**: Multi-stage build for optimized production image
- **.dockerignore**: Excludes unnecessary files from Docker context
- **docker-compose.yml**: Complete development environment with PostgreSQL, RabbitMQ, and the Spring Boot application

## Quick Start

### Option 1: Using Docker Compose (Recommended for Development)

Run the entire stack with the following command:

```bash
docker-compose up --build
```

This starts:
- **PostgreSQL** on port 5432
- **RabbitMQ** on port 5672 (with Management UI on port 15672)
- **Spring Boot Application** on port 8080

Access the services:
- Application Health: `http://localhost:8080/actuator/health`
- Notification Status: `http://localhost:8080/api/notifications/status`
- RabbitMQ Management: `http://localhost:15672` (default: guest/guest)

### Option 2: Building and Running Docker Image Manually

Build the image:

```bash
docker build -t atx24-app:latest .
```

Run the container:

```bash
docker run -p 8080:8080 atx24-app:latest
```

Note: Without external PostgreSQL and RabbitMQ, the application may fail to connect. Use docker-compose for full integration.

## Communication Module - Example Requests

The module provides REST endpoints to receive notifications from OpenMRS or external systems.

### 1. Check Notification Service Status

```bash
curl -X GET http://localhost:8080/api/notifications/status
```

**Response:**
```json
{
  "status": "UP",
  "message": "Notification service is operational",
  "timestamp": 1715155200000
}
```

### 2. Send a Notification (OpenMRS Patient Alert)

```bash
curl -X POST http://localhost:8080/api/notifications/receive \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Patient Update Alert",
    "message": "Patient John Doe (ID: 12345) vital signs have changed significantly",
    "type": "ALERT",
    "source": "OPENMRS",
    "recipientId": "doctor-001"
  }'
```

**Response:**
```json
{
  "id": "a3f4b2c1-d5e6-47f8-9a0b-1c2d3e4f5g6h",
  "title": "Patient Update Alert",
  "message": "Patient John Doe (ID: 12345) vital signs have changed significantly",
  "type": "ALERT",
  "source": "OPENMRS",
  "recipientId": "doctor-001",
  "createdAt": "2026-05-08T14:30:00",
  "sentAt": "2026-05-08T14:30:00.123",
  "status": "SENT"
}
```

### 3. Send a Prescription Notification

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "title": "New Prescription",
    "message": "Prescription for patient Jane Smith: Amoxicillin 500mg, 3 times daily for 7 days",
    "type": "INFO",
    "source": "OPENMRS",
    "recipientId": "pharmacist-002"
  }'
```

### 4. Send a Lab Result Notification

```bash
curl -X POST http://localhost:8080/api/notifications/receive \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Lab Results Available",
    "message": "Blood test results for patient ID P-98765 are ready for review",
    "type": "INFO",
    "source": "OPENMRS",
    "recipientId": "lab-tech-003"
  }'
```

### 5. Send a Critical Warning

```bash
curl -X POST http://localhost:8080/api/notifications/receive \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Critical: Medication Interaction",
    "message": "WARNING: Patient has a potential medication interaction. Verify medication list before proceeding.",
    "type": "WARNING",
    "source": "OPENMRS",
    "recipientId": "doctor-001"
  }'
```

## RabbitMQ Message Flow

1. **Receive**: Notification arrives via HTTP POST to `/api/notifications/receive`
2. **Process**: NotificationService validates and creates a Notification object
3. **Publish**: Notification is published to RabbitMQ exchange
4. **Queue**: Message is placed in `notification.queue` for processing
5. **Handle**: NotificationListener consumes message and triggers handlers

### Monitor RabbitMQ

Access RabbitMQ Management Console:
- URL: `http://localhost:15672`
- Username: `guest`
- Password: `guest`

View queued messages and monitor message flow in real-time.

## Environment Variables

When running with docker-compose, the following environment variables are automatically configured:

- `SPRING_DATASOURCE_URL`: PostgreSQL connection URL
- `SPRING_DATASOURCE_USERNAME`: PostgreSQL username
- `SPRING_DATASOURCE_PASSWORD`: PostgreSQL password
- `SPRING_RABBITMQ_HOST`: RabbitMQ host
- `SPRING_RABBITMQ_PORT`: RabbitMQ port
- `SPRING_RABBITMQ_USERNAME`: RabbitMQ username
- `SPRING_RABBITMQ_PASSWORD`: RabbitMQ password

You can override these by creating a `.env` file or passing `-e` flags to docker-compose.

## API Endpoints Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/notifications/receive` | Receive a notification from external system |
| POST | `/api/notifications` | Alternative endpoint for creating notifications |
| GET | `/api/notifications/health` | Check notification service health |
| GET | `/api/notifications/status` | Get detailed service status |

## Cleanup

Stop all services:

```bash
docker-compose down
```

Remove volumes (PostgreSQL data, RabbitMQ data):

```bash
docker-compose down -v
```

## Troubleshooting

If the application fails to start, check the logs:

```bash
docker-compose logs -f app
```

For RabbitMQ issues:

```bash
docker-compose logs -f rabbitmq
```

For PostgreSQL issues:

```bash
docker-compose logs -f postgres
```

### Common Issues

**Connection Refused**: Ensure all services have started and are healthy
```bash
docker-compose ps
```

**RabbitMQ not responding**: Check RabbitMQ health
```bash
docker-compose exec rabbitmq rabbitmq-diagnostics -q ping
```

**Application startup error**: View full application logs
```bash
docker-compose logs app
```

## Production Deployment

For production, consider:

1. Using externalized secrets management (AWS Secrets Manager, HashiCorp Vault, etc.)
2. Setting appropriate resource limits in docker-compose or Kubernetes manifests
3. Implementing proper notification handlers (email, SMS, push notifications)
4. Adding persistence layer (save notifications to database)
5. Implementing retry logic for failed notifications
6. Setting up monitoring and alerting for the communication module
3. Using a container registry (Docker Hub, ECR, GCR) for image storage
4. Implementing proper logging and monitoring
5. Using health checks with appropriate timeouts for your service latency

