# Notification Service

This project is an event-driven notification service built with Spring Boot. Its job is to receive notification events, decide whether they should be delivered, persist delivery state, and send messages through different channels such as email, SMS, and push notification.

The service is designed to solve a common backend problem:

- upstream systems want to publish business events such as `ORDER_SHIPPED`, `PAYMENT_FAILED`, or `PASSWORD_RESET`
- users should receive the right message on the right channel
- duplicate events should not create duplicate notifications
- notification delivery should respect user preferences and rate limits
- delivery status should be traceable after the fact

This repository already contains the core implementation for those ideas. It also contains planning notes in `plans/`, but this README and the files in `docs/` are meant to be the main developer-facing documentation.

## What This Service Does

At a high level, the service:

1. consumes notification events from Kafka
2. checks idempotency so the same event is not processed twice
3. resolves the recipient information
4. loads the matching active templates for the event type
5. applies notification preferences and rate limits
6. creates one notification record per channel
7. updates unread counters for registered users
8. dispatches the notification to the correct channel sender
9. updates delivery status to `SENT`, `FAILED`, or `RATE_LIMITED`

Unexpected Kafka processing failures are retried and, if they still fail, moved
to a dead-letter topic instead of being silently lost.

The project also includes a dedicated DLT consumer so failed records become
visible in the application logs for local debugging and early operational use.

It also exposes REST APIs for:

- managing templates
- managing notification preferences
- registering device tokens for push notifications
- reading and updating unread notification state
- publishing test events in development
- minting temporary dev JWTs in `dev` or `local` profiles

## Why The Project Is Structured This Way

This service follows a few important design decisions:

### 1. Event-driven first

The core notification flow starts from Kafka, not from a synchronous API call. This keeps the notification pipeline decoupled from upstream business services.

### 2. Persist before sending

A notification row is saved in PostgreSQL before the actual delivery call is made. This gives the system an audit trail and lets the service track delivery status per channel.

### 3. Template-driven content

Notification content is stored in `notification_templates`. Upstream systems send event data and metadata, while this service turns that into user-facing messages by hydrating templates.

### 4. Per-channel independence

Email, SMS, and push are treated as separate deliveries. One channel can fail while another succeeds, and the database keeps those outcomes separate.

### 5. Fast mutable state in Redis

Redis is used for:

- idempotency checks
- unread count caching
- sliding-window rate limiting

### 6. JWT resource-server security

This service is secured as a resource server. Protected APIs read the authenticated user from JWT claims instead of trusting raw client-supplied `userId` values.

## Architecture At A Glance

The system is built around a small set of responsibilities:

- `controller/`
  Exposes REST endpoints.
- `security/`
  Validates JWTs, maps roles, and extracts the authenticated user ID.
- `messaging/`
  Publishes and consumes Kafka events.
- `service/`
  Contains the main business logic.
- `delivery/`
  Contains channel-specific sender implementations.
- `cache/`
  Handles Redis-backed idempotency and unread counters.
- `ratelimit/`
  Implements Redis-backed sliding-window rate limiting.
- `model/` and `repository/`
  Define and persist the domain model.

## Main Flow

The most important class in the project is `NotificationProcessingService`. That class orchestrates the notification pipeline.

The end-to-end flow looks like this:

1. An upstream system or local test endpoint publishes a `NotificationEvent` into the Kafka topic `notification-events`.
2. `NotificationConsumer` receives the event.
3. Producer-level and user-event-level rate limits are checked.
4. `NotificationProcessingService` checks Redis idempotency.
5. The service resolves recipient data:
   - for `REGISTERED_USER`, it loads the user from PostgreSQL
   - for `GUEST`, it uses `guestUserDetails` from the event itself
6. The service loads all active templates for the event type.
7. Notification preferences are loaded for registered users.
8. Event-level or channel-level mutes are applied.
9. For each allowed template, the service hydrates placeholders such as `{firstName}` from metadata and resolved contact info.
10. A `Notification` row is saved with status `PENDING`.
11. The unread counter is incremented in Redis for registered users.
12. `DeliveryManagerService` picks the correct `ChannelSender`.
13. The sender calls AWS SES, Twilio, or Firebase.
14. Delivery status is updated in PostgreSQL.

For a fuller walkthrough, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- PostgreSQL
- Spring Data Redis
- Redis
- Spring Kafka
- Kafka + Zookeeper
- Spring Security OAuth2 Resource Server
- JWT (local symmetric signing for dev bootstrap)
- ModelMapper
- AWS SES
- Twilio
- Firebase Admin SDK
- Springdoc OpenAPI / Swagger UI
- JUnit 5 + Mockito

## Effective API Base Path

The effective REST base path is:

`/api/v1`

This is not duplicated in every controller annotation. Instead, it is applied centrally by `WebConfig`, which prepends `/api/v1` to every `@RestController`.

That means:

- controller classes may declare paths like `/templates`
- clients still call `/api/v1/templates`

For the complete endpoint reference, see [docs/API_REFERENCE.md](docs/API_REFERENCE.md).

## Roles And Security Model

The service uses JWT Bearer tokens with a simple claims model:

- `sub`
  the user UUID
- `roles`
  values such as `ROLE_USER`, `ROLE_ADMIN`, `ROLE_INTERNAL`
- `email`
  optional debugging or informational claim

Current authorization model:

- `ROLE_USER` or `ROLE_ADMIN`
  can access notification state, preferences, and device token APIs
- `ROLE_ADMIN`
  can manage templates
- `ROLE_INTERNAL` or `ROLE_ADMIN`
  can call the test publish endpoint
- dev token issuing endpoint
  available only when the app runs with `dev` or `local` profile

Important note:

- the route `/api/v1/dev-auth/token` is permitted by security
- the controller only exists when the Spring profile is `dev` or `local`

## Core Domain Concepts

### Notification Event

This is the incoming message that tells the service what happened. It contains:

- producer name
- recipient type
- user ID or guest details
- event type
- correlation ID
- idempotency key
- metadata used to hydrate templates

### Template

Templates are identified by:

- `eventType`
- `deliveryChannel`

This means the same event can have separate templates for email, SMS, and push.

### Notification

A notification record is the concrete delivery attempt for one channel. It stores:

- the resolved recipient
- the hydrated title and message
- correlation and idempotency identifiers
- read status
- network delivery status
- metadata snapshot

### Preference

Preferences are stored per user and contain:

- muted channels
- muted event types

### Device Token

Device tokens map a user to platform-specific push tokens such as:

- iOS
- Android
- Web

## Local Infrastructure

The project ships with `docker-compose.yml` for the main infrastructure services:

- PostgreSQL
- Redis
- Zookeeper
- Kafka
- RedisInsight

RedisInsight is optional, but it is helpful when debugging Redis keys manually.

For Kafka dead-letter inspection, you can also read the DLT topic directly:

```bash
kafka-console-consumer --bootstrap-server localhost:9092 --topic notification-events.dlt --from-beginning
```

If you need to recover a failed event after fixing the root cause, you can replay
the dead-letter record through `POST /api/v1/internal/recovery/dlt/replay`.
The recovery flow fetches the original DLT record by topic/partition/offset,
supports dry-run preview mode, and regenerates the idempotency key by default so
the Redis deduplication layer does not reject the replayed event as a duplicate.

## Required Local Configuration

The service expects a mixture of environment variables and one local Firebase credential file.

### Environment variables

You should provide these locally:

- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `AWS_ACCESS_KEY`
- `AWS_SECRET_KEY`
- `AWS_VERIFIED_EMAIL`
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_FROM_NUMBER`
- `JWT_SECRET`
  optional because `application.yml` has a default, but you should still set a proper local value
- `JWT_ISSUER`
  optional
- `JWT_EXPIRATION_MINUTES`
  optional
- `SPRING_PROFILES_ACTIVE`
  set to `local` or `dev` if you want the dev token endpoint

To keep Docker and Spring aligned, use:

- `DB_NAME=notification_db`

### Firebase credentials

Firebase initialization reads:

- `src/main/resources/firebase-adminsdk.json`

That file is ignored by Git and should stay local.

### Secrets hygiene

Do not commit:

- `.env`
- provider keys
- Firebase service account JSON

The repo already ignores the main local secret files.

## How To Start The Project Locally

### 1. Start infrastructure

```bash
docker compose up -d postgres redis zookeeper kafka redisinsight
```

### 2. Make sure your local environment variables are loaded

If you use a `.env` loader in your shell, load it before starting the app. The project also includes `spring-dotenv`, which helps with local environment handling.

### 3. Run the Spring Boot application

If your local Maven installation is available:

```bash
mvn spring-boot:run
```

If you prefer the wrapper, note the current limitation described below in the testing section.

You can also run the `Application` main class directly from your IDE.

### 4. Optional: enable local dev token issuing

Run with:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

or use the same profile in your IDE run configuration.

### 5. Open Swagger UI

Once the app is up:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Example Local Workflow

One practical way to test the project manually is:

1. start Docker services
2. run the app with `SPRING_PROFILES_ACTIVE=local`
3. call `POST /api/v1/dev-auth/token` to mint a JWT
4. use that token to create templates
5. register a device token if you want to test push
6. publish a test event with `POST /api/v1/test/publish`
7. inspect PostgreSQL rows, Redis keys, and provider logs

## Current Testing Status

The repository already has unit tests for:

- caches
- messaging layer
- rate limiting service
- template service
- notification processing
- notification state
- preference service
- device token service
- authenticated user extraction
- template controller
- basic application startup

There is not yet equivalent controller coverage for every user-facing endpoint, and there are not yet full end-to-end tests with real infrastructure.

For detailed guidance, see [docs/TESTING.md](docs/TESTING.md).

## Current Known Limitations

### Maven wrapper is incomplete

The repository includes `mvnw`, but the `.mvn/wrapper/` metadata is currently missing. That means `./mvnw` may fail until the wrapper files are restored or regenerated.

### Schema migration tool is not yet present

Database schema is currently managed through:

- Hibernate `ddl-auto=update`

That is convenient for development, but a production-grade migration tool such as Flyway or Liquibase is not yet part of this repo.

### Some tests are intentionally narrow

For example, standalone controller tests focus on controller behavior and exception mapping, not the full MVC + security + global prefix composition.

### Push relies on external credentials and device data

Push delivery requires:

- valid Firebase credentials
- valid stored device tokens

### Test publishing endpoint is primarily for local development

The `/api/v1/test/publish` route exists to help local testing. It is not meant to be the main production ingress path for upstream systems.

## Documentation Map

- [docs/API_REFERENCE.md](docs/API_REFERENCE.md)
  Full REST API reference with example payloads and error formats.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
  Internal system flow, design choices, data model, and delivery pipeline.
- [docs/TESTING.md](docs/TESTING.md)
  Testing philosophy, current coverage, and how to run tests.

## Suggested Reading Order For A New Developer

1. Read this `README.md`
2. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
3. Read [docs/API_REFERENCE.md](docs/API_REFERENCE.md)
4. Read [docs/TESTING.md](docs/TESTING.md)
5. Browse the `plans/` folder if you want historical design context
