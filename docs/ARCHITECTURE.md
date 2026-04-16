# Architecture Guide

This document explains how the notification service works internally, why it is structured the way it is, and how the main components collaborate.

## Big Picture

The service is a small orchestration engine for outbound notifications.

It combines:

- Kafka for asynchronous event intake
- PostgreSQL for durable application state
- Redis for fast ephemeral state
- provider SDKs for real delivery
- REST APIs for administration and user-facing notification controls

The goal is not only to send messages, but to send them safely and observably.

That means the system tries to answer:

- should this event be processed at all?
- which channels should be used?
- should this user receive this event?
- has this event already been processed?
- did the provider call succeed?
- how many unread notifications does the user currently have?

## Core Architectural Choices

### 1. Event-driven ingestion

Instead of tightly coupling notification sending to upstream HTTP calls, upstream systems can publish events. That gives better decoupling and makes the notification service easier to scale independently.

### 2. Template-based rendering

The service stores templates separately from the incoming event. Upstream systems do not need to send final human-readable strings for every channel. They only need to send:

- the event type
- who the recipient is
- metadata for placeholders

### 3. Persist first, then deliver

Each allowed delivery becomes a `Notification` row before the provider call is made. This is important because it gives:

- auditability
- retry visibility
- per-channel status tracking
- a place to store hydrated message content and metadata snapshot

### 4. Redis for fast operational concerns

Redis is used for things that need very low latency or are naturally temporary:

- idempotency locks
- unread counts
- rate limiting windows

### 5. JWT-derived user identity

User-facing endpoints do not trust raw client-provided `userId`. The authenticated user is derived from the JWT `sub` claim. This protects against user impersonation via forged IDs in requests.

### 6. Channel abstraction

Email, SMS, and push are implemented behind a common `ChannelSender` interface. This makes `DeliveryManagerService` independent from provider-specific details.

## Main Runtime Components

## Inbound Layer

### `NotificationProducer`

This service publishes test events into the Kafka topic `notification-events`.

In the current codebase, it is mainly used by `TestEventController` for local development.

### `NotificationConsumer`

This is the Kafka listener. Its job is intentionally narrow:

- receive the event from Kafka
- apply producer-level rate limiting
- apply user-event-level rate limiting
- delegate to `NotificationProcessingService`

It does not contain the main business logic itself.

## Orchestration Layer

### `NotificationProcessingService`

This is the core of the system.

It is responsible for:

- idempotency check
- recipient resolution
- template lookup
- preference resolution
- event mute and channel mute checks
- template hydration
- notification persistence
- unread counter updates
- dispatch delegation

This class is the best place to start if someone wants to understand the heart of the project.

### `DeliveryManagerService`

This service receives a fully prepared `Notification` and decides which sender implementation should handle it.

It also applies per-channel rate limiting before delivery and updates the final network status.

## Delivery Layer

### `EmailSender`

Uses AWS SES to send an email.

### `SmsSender`

Uses Twilio to send an SMS.

### `FcmPushSender`

Loads device tokens for a user and sends Firebase Cloud Messaging push notifications.

## State And User Interaction Layer

### `NotificationStateService`

Supports:

- mark one notification as read
- mark all notifications as read

It updates both PostgreSQL and the Redis unread counter.

### `NotificationPreferenceService`

Handles user-level mute settings for:

- channels
- event types

### `DeviceTokenService`

Handles push token registration and deletion for the authenticated user.

## Security Layer

### `SecurityConfig`

Defines:

- stateless JWT authentication
- route authorization rules
- custom 401 and 403 JSON responses

### `AuthenticatedUserService`

Extracts the authenticated user ID from the JWT and performs ownership checks where needed.

### `DevAuthController` and `JwtTokenService`

Provide a temporary local token bootstrap flow for development. This is useful before a separate auth service exists.

## Data Model

## `User`

Represents a registered user with:

- UUID
- first name
- last name
- email
- phone number

This entity uses soft delete.

## `NotificationTemplate`

Represents a reusable message template. Important fields:

- `eventType`
- `title`
- `body`
- `deliveryChannel`
- `defaultPriority`
- `isActive`

Important design point:

- one template is identified by `eventType + deliveryChannel`

This allows separate email, SMS, and push versions of the same event.

This entity uses soft delete.

## `Notification`

Represents one concrete channel-specific notification attempt.

Important fields:

- recipient info
- correlation ID
- idempotency key
- event type
- delivery channel
- hydrated title and message
- metadata snapshot
- user read status
- network delivery status
- producer name

Important design point:

- one business event can create multiple `Notification` rows
- for example, one for `EMAIL` and one for `SMS`

This entity also has a database-level unique constraint on:

- `idempotencyKey + deliveryChannel`

This complements the Redis idempotency guard.

This entity uses soft delete.

## `NotificationPreference`

Stores:

- muted channels
- muted events

The primary key is the user ID, which makes it effectively a one-preference-record-per-user design.

This entity uses soft delete.

## `DeviceToken`

Stores:

- user ID
- push token
- device type

Unlike some other entities in the system, device tokens are deleted through repository deletion rather than soft delete logic.

## End-To-End Event Flow

Here is the full flow in plain English.

### Step 1. Event enters Kafka

An upstream producer sends a `NotificationEvent` to the `notification-events` topic.

That event contains:

- the type of event
- who the recipient is
- the idempotency key
- optional metadata for placeholders

### Step 2. Kafka consumer receives the event

`NotificationConsumer` receives the message via `@KafkaListener`.

Before doing deeper work, it applies two rate-limit checks:

- producer-level
- user-event-level

If either check fails, processing stops early.

If deeper processing throws an exception, the listener now lets the failure
propagate to Spring Kafka's error handler so the record can be retried and then
sent to a dead-letter topic if it still cannot be processed.

### Step 3. Idempotency check

`NotificationProcessingService` asks `IdempotencyCache` whether the event's idempotency key has already been seen.

How it works:

- Redis key format: `idem:<idempotencyKey>`
- TTL: 24 hours
- implementation uses `SETNX` semantics through `setIfAbsent`

If the key already exists, the event is treated as a duplicate and is dropped.

### Step 4. Resolve contact info

The service supports two recipient types.

#### Registered users

If `recipientType = REGISTERED_USER`:

- `userId` must be present
- the service loads the user from PostgreSQL
- first name, last name, email, and phone are resolved from the user record

#### Guests

If `recipientType = GUEST`:

- `guestUserDetails` must be present
- contact data is taken directly from the event payload

### Step 5. Load templates

The service loads:

- all active templates for the event type

If no active templates exist, processing stops.

### Step 6. Load preferences

For registered users:

- preferences are loaded from PostgreSQL
- if none exist, the service uses a default empty preference object

For guests:

- no stored preference record is used

### Step 7. Apply event and channel rules

The service applies the following rules before creating notifications:

- if the event type is muted, stop processing entirely
- if a channel is muted, skip just that channel
- if the channel is push and the recipient is a guest, skip it because guests do not have stored device tokens

### Step 8. Hydrate the template

The service builds a metadata map that combines:

- event metadata
- resolved `firstName`
- resolved `lastName` when available

It then replaces placeholders like:

- `{firstName}`
- `{orderId}`

inside the template title and body.

### Step 9. Save notification row

For each allowed template, the service creates a `Notification` entity with:

- recipient info
- hydrated title
- hydrated message
- `PENDING` delivery status
- `UNREAD` read status
- event/correlation/idempotency identifiers

That row is saved to PostgreSQL.

### Step 10. Update unread count

For registered users, the service increments the unread Redis counter.

How it works:

- key format: `unread:<userId>`
- guests do not use this counter because they have no persistent user ID in this context

### Step 11. Dispatch delivery

`DeliveryManagerService` receives the saved notification and performs:

- per-channel rate limiting
- sender lookup
- actual provider call
- delivery status update

## Rate Limiting Design

Rate limiting uses a Redis sorted-set sliding-window algorithm.

### Why sorted sets

Each request is stored with its timestamp as the score. This allows the service to:

- remove entries that are older than the current window
- count current requests exactly
- make an allow or reject decision with a precise sliding window

### Current limits in code

#### Producer level

- key: `rate:producer:<producerName>`
- limit: 100 requests per 10 seconds

#### User event level

- key: `rate:user:<userId>:<eventType>`
- limit: 7 requests per 10 minutes

#### Per-channel

- email: 5 per hour per user
- SMS: 5 per hour per user

Current implementation note:

- `isChannelAllowed` returns early for `PUSH_NOTIFICATION`
- so push currently bypasses the per-channel limiter in practice

That is worth knowing if someone is troubleshooting unexpected push throughput.

## Failure Handling And DLT

The Kafka consumer now uses a retry + dead-letter-topic flow for unexpected
processing failures.

The behavior is:

1. a record is consumed from `notification-events`
2. if processing throws unexpectedly, Spring Kafka retries it
3. after the configured retry attempts are exhausted, the original record is
   published to a dead-letter topic

Current dead-letter naming rule:

- main topic: `notification-events`
- DLT topic: `notification-events.dlt`

Important distinction:

- intentional business drops such as producer throttling or user-event
  throttling are returned early and do not go to the DLT
- unexpected processing failures are retried and then dead-lettered

Operationally, the service now also includes a dedicated DLT consumer. Its job
is to listen to `notification-events.dlt` and log:

- Kafka topic
- partition
- offset
- record key
- correlation ID
- event type
- full event payload
- dead-letter headers

This gives developers a very lightweight first step for reading failed records
without building a full replay dashboard yet.

Recovery path:

- inspect the failed payload from the DLT logs or Kafka tooling
- fix the underlying code/data/config problem
- replay the event through `POST /api/v1/internal/recovery/dlt/replay`
- the recovery endpoint fetches the original dead-letter record by topic,
  partition, and offset
- it supports `dryRun` preview mode for safer operational checks
- by default the replay flow generates a fresh idempotency key so the Redis
  deduplication layer does not drop the replay immediately
- replay attempts are capped per dead-letter record using Redis-backed guardrails

## Idempotency Design

This service uses two layers of protection against duplicates.

### Layer 1. Redis idempotency cache

Fast early-drop mechanism based on the event idempotency key.

### Layer 2. Database unique constraint

The `Notification` table also protects uniqueness at the persistence level with:

- `idempotencyKey + deliveryChannel`

Together, these give a strong practical defense against duplicate notifications.

## Unread Counter Design

Unread counts are kept in Redis for fast retrieval.

### Why use Redis here

Unread counts are read frequently by UI surfaces such as bell badges. Redis avoids repetitive counting queries against PostgreSQL.

### Update rules

- increment when a new notification is saved
- decrement when one unread notification is marked read
- reset to `0` when all unread notifications are marked read
- clamp negative values back to `0`

## Security Design

The application is a JWT resource server.

### What that means

- clients send Bearer tokens
- Spring Security validates them
- application code reads claims from the authenticated principal

### Why this approach

It keeps authentication separate from business logic and fits well with a microservice-style architecture.

### Current roles

- `ROLE_USER`
- `ROLE_ADMIN`
- `ROLE_INTERNAL`

### Effective route protection

- `/api/v1/templates/**`
  admin only
- `/api/v1/test/**`
  internal or admin
- `/api/v1/notifications/**`
  user or admin
- `/api/v1/notification/preferences/**`
  user or admin
- `/api/v1/devices/**`
  user or admin

### Dev bootstrap path

The service can mint local JWTs through:

- `/api/v1/dev-auth/token`

but only when the `dev` or `local` profile is active.

## External Provider Integration

## AWS SES

Used for email delivery.

Configured through:

- `aws.ses.region`
- `aws.ses.access-key`
- `aws.ses.secret-key`
- `aws.ses.from-email`

## Twilio

Used for SMS delivery.

Configured through:

- `twilio.account-sid`
- `twilio.auth-token`
- `twilio.from-number`

## Firebase Admin SDK

Used for push notification delivery.

Configured by loading:

- `src/main/resources/firebase-adminsdk.json`

## Persistence And Deletion Strategy

Some domain entities use soft delete:

- `User`
- `Notification`
- `NotificationTemplate`
- `NotificationPreference`

This means delete operations mark the row deleted rather than physically removing it, and normal queries filter deleted rows out automatically.

This is useful for:

- auditability
- safer operational recovery

## What Is Not Yet Built

A new developer should know that the project is already functional, but still evolving.

Examples of current gaps or simplifications:

- no Flyway or Liquibase migrations yet
- no full end-to-end test suite with Testcontainers
- no full security integration test coverage yet
- local dev auth is still a bootstrap convenience, not the final auth architecture
- local test event publishing is mainly a development helper, not the primary production ingress pattern

## Where To Go Next In The Code

If you want to learn the system by reading code, a good order is:

1. `NotificationEvent`
2. `NotificationConsumer`
3. `NotificationProcessingService`
4. `DeliveryManagerService`
5. `ChannelSender` implementations
6. `SecurityConfig`
7. `GlobalExceptionHandler`
