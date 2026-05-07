# Docker Setup Guide

This project includes Docker configuration for easy containerization and local development.

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
- Application: `http://localhost:8080`
- Application Health: `http://localhost:8080/actuator/health`
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

## Production Deployment

For production, consider:

1. Using externalized secrets management (AWS Secrets Manager, HashiCorp Vault, etc.)
2. Setting appropriate resource limits in docker-compose or Kubernetes manifests
3. Using a container registry (Docker Hub, ECR, GCR) for image storage
4. Implementing proper logging and monitoring
5. Using health checks with appropriate timeouts for your service latency

