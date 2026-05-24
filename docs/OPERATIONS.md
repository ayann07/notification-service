# Operations Guide

This document captures the first operational layer for the notification service: containerization and continuous integration.

## Docker

The app image is built by the root `Dockerfile`.

Key choices:

- multi-stage build so Maven and the JDK stay out of the runtime image
- Java 21 runtime image to match the project target
- non-root container user
- only the packaged Spring Boot jar is copied into the final image
- `.dockerignore` keeps local build output, secrets, Git metadata, and documentation out of the Docker build context

Build the image:

```bash
docker build -t notification-service:local .
```

Run the full local stack:

```bash
docker compose up --build
```

The compose stack starts:

- `app` on `localhost:8080`
- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- Kafka on `localhost:9092`
- RedisInsight on `localhost:5540`

Inside Docker, the app uses the `docker` Spring profile from `application-docker.yml`. That profile switches infrastructure hosts from `localhost` to Docker service names such as `postgres`, `redis`, and `kafka`.

## CI Pipeline

The GitHub Actions workflow lives at `.github/workflows/ci.yml`.

It runs on:

- pushes to `main`
- pushes to `master`
- pull requests

The pipeline has three jobs.

## Unit Test And Package

This job:

1. checks out the repository
2. installs Java 21
3. caches Maven dependencies
4. runs `./mvnw -B test`
5. runs `./mvnw -B -DskipTests package`
6. uploads the built jar as a short-lived artifact

This is the fast PR gate. Tests named `*Test` should be deterministic unit, service, controller, and messaging tests that do not require real infrastructure.

## Integration Test

This job runs after unit tests on pushes to `main` or `master`, and when the workflow is triggered manually.

It:

1. starts PostgreSQL, Redis, Zookeeper, and Kafka service containers
2. runs `./mvnw -B verify -Pintegration`
3. executes tests named `*IT`

Provider credentials in integration CI are dummy values. They exist only so the Spring context can start without putting real secrets in the repository.

The current integration test boots the real application, creates a template through the secured HTTP API, publishes a notification event through Kafka, waits for the consumer to process it, and verifies the hydrated notification in PostgreSQL. External delivery is spied so CI does not call AWS SES.

## Docker Image

This job runs only after unit tests pass.

It:

1. checks out the repository
2. sets up Docker Buildx
3. builds the Docker image without pushing it
4. validates the compose file with `docker compose config --quiet`

This is CI, not deployment. No registry push or production release happens yet.

CD is handled separately in `.github/workflows/cd.yml`. It builds the image, pushes it to Amazon ECR, and updates the Kubernetes deployment in EKS. See [KUBERNETES.md](KUBERNETES.md).

## Test Naming

Use this convention:

- `*Test` for fast tests that run on every pull request
- `*IT` for heavier integration tests that need real infrastructure
