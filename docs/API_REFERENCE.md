# API Reference

This document describes the effective REST API exposed by the notification service.

## How Routing Works

All `@RestController` classes are automatically prefixed with:

`/api/v1`

That prefix is applied centrally through `WebConfig`.

So even though a controller may declare `@RequestMapping("/templates")`, the effective public route is:

`/api/v1/templates`

## Authentication

Protected endpoints expect:

```http
Authorization: Bearer <jwt>
```

JWT claims used by the application:

- `sub`
  authenticated user UUID
- `roles`
  authorities such as `ROLE_USER`, `ROLE_ADMIN`, `ROLE_INTERNAL`
- `email`
  optional

## Role Map

- template management: `ROLE_ADMIN`
- notification preferences: `ROLE_USER` or `ROLE_ADMIN`
- device token management: `ROLE_USER` or `ROLE_ADMIN`
- notification state endpoints: `ROLE_USER` or `ROLE_ADMIN`
- test publish endpoint: `ROLE_INTERNAL` or `ROLE_ADMIN`
- dev token endpoint: permitted by security, but only available when running with `dev` or `local` profile

## Common Error Format

Most controller and service exceptions are translated by `GlobalExceptionHandler`.

Example:

```json
{
  "timestamp": "2026-04-14T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/templates",
  "validationErrors": {
    "eventType": "Event type cannot be blank",
    "deliveryChannel": "Channels list cannot be null"
  }
}
```

Security-generated errors follow the same overall structure but usually return `validationErrors: null`.

## Dead-Letter Topic Workflow

The project now includes operational support for the Kafka dead-letter topic.

Main topic:

- `notification-events`

Dead-letter topic:

- `notification-events.dlt`

How failed records are handled:

1. the main consumer retries unexpected processing failures
2. if retries are exhausted, Spring Kafka publishes the original record to the DLT
3. `NotificationDltConsumer` listens to the DLT and logs failed records with Kafka metadata and headers

For local inspection outside the app, you can also consume the DLT topic
directly with Kafka tooling:

```bash
kafka-console-consumer --bootstrap-server localhost:9092 --topic notification-events.dlt --from-beginning
```

## Dev Authentication

### POST `/api/v1/dev-auth/token`

Creates a local JWT for testing protected APIs.

Availability:

- only when Spring profile is `dev` or `local`

Auth required:

- no

Request body:

```json
{
  "userId": "6a077ec7-3f5f-4e8a-98ca-06fd03e68e10",
  "email": "admin@example.com",
  "roles": ["ROLE_ADMIN"]
}
```

Response:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 7200
}
```

Notes:

- this is a bootstrap convenience for local development
- it is not intended to be the long-term production authentication mechanism

## Template Management

These endpoints are intended for admins.

### GET `/api/v1/templates`

Returns all templates.

Auth required:

- `ROLE_ADMIN`

Response:

```json
[
  {
    "eventType": "ORDER_SHIPPED",
    "title": "Order shipped",
    "body": "Hi {firstName}, your order is on the way",
    "deliveryChannel": "EMAIL",
    "defaultPriority": 3
  }
]
```

### GET `/api/v1/templates/{eventType}/{deliveryChannel}`

Returns one template identified by:

- `eventType`
- `deliveryChannel`

Auth required:

- `ROLE_ADMIN`

Example:

`GET /api/v1/templates/ORDER_SHIPPED/EMAIL`

Response:

```json
{
  "eventType": "ORDER_SHIPPED",
  "title": "Order shipped",
  "body": "Hi {firstName}, your order is on the way",
  "deliveryChannel": "EMAIL",
  "defaultPriority": 3
}
```

Common failure:

- `404 Not Found` if that event/channel pair does not exist

### POST `/api/v1/templates`

Creates a new template.

Auth required:

- `ROLE_ADMIN`

Request body:

```json
{
  "eventType": "ORDER_SHIPPED",
  "title": "Order shipped",
  "body": "Hi {firstName}, your order is on the way",
  "deliveryChannel": "EMAIL",
  "defaultPriority": 3
}
```

Response:

```json
{
  "eventType": "ORDER_SHIPPED",
  "title": "Order shipped",
  "body": "Hi {firstName}, your order is on the way",
  "deliveryChannel": "EMAIL",
  "defaultPriority": 3
}
```

Common failures:

- `400 Bad Request` for validation errors
- `409 Conflict` if the same `eventType + deliveryChannel` already exists

### PUT `/api/v1/templates/{eventType}/{deliveryChannel}`

Updates an existing template.

Auth required:

- `ROLE_ADMIN`

Important contract:

- the path identifies the template to update
- the request body must use the same `eventType` and `deliveryChannel`

Request body:

```json
{
  "eventType": "ORDER_SHIPPED",
  "title": "Order update",
  "body": "Hi {firstName}, order {orderId} has shipped",
  "deliveryChannel": "EMAIL",
  "defaultPriority": 2
}
```

Response:

```json
{
  "eventType": "ORDER_SHIPPED",
  "title": "Order update",
  "body": "Hi {firstName}, order {orderId} has shipped",
  "deliveryChannel": "EMAIL",
  "defaultPriority": 2
}
```

Common failures:

- `400 Bad Request` if path and body identity do not match
- `404 Not Found` if the template does not exist

### DELETE `/api/v1/templates/{eventType}/{deliveryChannel}`

Deletes one template.

Auth required:

- `ROLE_ADMIN`

Response:

Plain text:

```text
Template ORDER_SHIPPED for channel EMAIL deleted successfully.
```

## Notification Preferences

These endpoints derive the user from the JWT subject.

### GET `/api/v1/notification/preferences/me`

Returns the current authenticated user's preference record.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Response shape:

The controller currently returns the entity directly, so the response may include persistence-related fields as well as the preference fields.

Example:

```json
{
  "userId": "6a077ec7-3f5f-4e8a-98ca-06fd03e68e10",
  "mutedChannels": ["SMS"],
  "mutedEvents": ["PROMOTIONAL_BANNER"],
  "createdAt": "2026-04-14T10:00:00",
  "updatedAt": "2026-04-14T11:00:00",
  "deletedAt": null,
  "deleted": false
}
```

Notes:

- if the user does not yet have a saved preference record, the service creates an in-memory default object with the current user ID and empty sets

### PATCH `/api/v1/notification/preferences/channel`

Mutes or unmutes one delivery channel.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Request body:

```json
{
  "deliveryChannel": "SMS",
  "mute": true
}
```

Response:

```json
{
  "userId": "6a077ec7-3f5f-4e8a-98ca-06fd03e68e10",
  "mutedChannels": ["SMS"],
  "mutedEvents": []
}
```

### PATCH `/api/v1/notification/preferences/event`

Mutes or unmutes one event type.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Request body:

```json
{
  "eventType": "ORDER_SHIPPED",
  "mute": true
}
```

Response:

```json
{
  "userId": "6a077ec7-3f5f-4e8a-98ca-06fd03e68e10",
  "mutedChannels": [],
  "mutedEvents": ["ORDER_SHIPPED"]
}
```

## Device Token APIs

These endpoints are used for push-notification registration.

### POST `/api/v1/devices/register`

Registers or refreshes a device token for the authenticated user.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Request body:

```json
{
  "token": "fcm-device-token",
  "deviceType": "ANDROID"
}
```

Response:

Plain text:

```text
Device token registered successfully
```

Behavior:

- if the token is new, a new `DeviceToken` row is created
- if the token already exists, the service refreshes its `updatedAt` timestamp

### DELETE `/api/v1/devices/unregister?token={token}`

Deletes a device token owned by the authenticated user.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Response:

Plain text:

```text
Device token deleted successfully
```

Common failure:

- `404 Not Found` if that token does not belong to the authenticated user

## Notification State APIs

These endpoints manage read state and unread counts.

### GET `/api/v1/notifications/unread-count`

Returns the unread count for the authenticated user.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Response:

```json
5
```

### POST `/api/v1/notifications/{notificationId}/mark-read`

Marks one notification as read for the authenticated user.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Response:

- HTTP `200 OK`
- empty body

Behavior:

- updates PostgreSQL if the notification belongs to the authenticated user and is still unread
- decrements the Redis unread counter only when a row was actually updated

### POST `/api/v1/notifications/mark-read`

Marks all unread notifications as read for the authenticated user.

Auth required:

- `ROLE_USER` or `ROLE_ADMIN`

Response:

- HTTP `200 OK`
- empty body

Behavior:

- updates all unread rows for the user in PostgreSQL
- resets the Redis unread counter to `0` when at least one row changed

## Test Event Publishing

### POST `/api/v1/test/publish`

Publishes a `NotificationEvent` into Kafka for local testing.

Auth required:

- `ROLE_INTERNAL` or `ROLE_ADMIN`

Request body:

```json
{
  "producerName": "ORDER_SERVICE",
  "recipientType": "REGISTERED_USER",
  "userId": "6a077ec7-3f5f-4e8a-98ca-06fd03e68e10",
  "eventType": "ORDER_SHIPPED",
  "correlationId": "order-42",
  "idempotencyKey": "order-42-email",
  "metadata": {
    "orderId": 42
  }
}
```

Response:

Plain text:

```text
Successfully pushed to Kafka! Check your terminal and your phone/email
```

Behavior:

- if `correlationId` is missing, the controller generates one

Current implementation note:

- the local `NotificationProducer` uses `event.getUserId().toString()` as the Kafka key
- because of that, this built-in test endpoint is currently most reliable for registered-user test events
- guest notification handling exists deeper in the processing layer, but the local publish helper is not yet optimized for guest event publishing

## Swagger And OpenAPI

These routes are publicly accessible:

- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`

They are useful for discovering the API interactively while developing locally.
