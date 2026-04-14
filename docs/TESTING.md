# Testing Guide

This document explains how testing currently works in the project, what kinds of tests already exist, how to run them, and what limitations a new developer should know about.

## Testing Philosophy

Most tests in this repository are focused unit tests built with:

- JUnit 5
- Mockito

The current testing style is intentionally practical:

- mock infrastructure dependencies
- test one class at a time
- focus on business branches and side effects
- keep tests beginner-friendly and readable

This means the suite is already useful for verifying business logic, but it is not yet a complete end-to-end system test suite.

## Current Test Coverage

The project currently includes these test classes:

## Boot / context

- `ApplicationTests`

Purpose:

- verifies that the Spring application context can start

## Controller tests

- `TemplateControllerTest`

Purpose:

- validates HTTP behavior for the template controller
- checks validation and exception-mapping behavior

Important note:

- this test is a standalone MockMvc test
- it does not boot the full application
- it focuses on controller behavior itself
- because of that, it does not exercise the global `/api/v1` prefix added by `WebConfig`
- it also does not exercise the full Spring Security filter chain

That is a normal and acceptable tradeoff for narrow controller tests, but it is important to understand what this test does and does not prove.

## Messaging tests

- `NotificationProducerTest`
- `NotificationConsumerTest`

Purpose:

- verify Kafka publishing delegation
- verify consumer behavior around rate limits and processing delegation

## Cache tests

- `IdempotencyCacheTest`
- `UnreadCounterCacheTest`

Purpose:

- verify Redis interaction logic without connecting to a real Redis instance

## Service tests

- `DeliveryManagerServiceTest`
- `DeviceTokenServiceTest`
- `NotificationPreferenceServiceTest`
- `NotificationProcessingServiceTest`
- `NotificationStateServiceTest`
- `TemplateServiceTest`

Purpose:

- verify the core business logic layer
- exercise orchestration, validation, persistence decisions, and edge cases

## Security tests

- `AuthenticatedUserServiceTest`

Purpose:

- verify JWT subject parsing
- verify ownership checks

## Rate limiting tests

- `RateLimitingServiceTest`

Purpose:

- verify rate-limit service behavior with mocked sliding-window logic

## What Is Covered Well Right Now

The strongest current coverage is in:

- notification processing orchestration
- template business rules
- preference toggling
- notification state updates
- cache behavior
- consumer delegation logic
- JWT user extraction logic

Those tests already give good confidence in much of the service layer.

## What Is Not Covered Yet

A new developer should know these gaps still exist:

- no controller tests yet for:
  - `DeviceTokenController`
  - `NotificationController`
  - `NotificationPreferenceController`
  - `DevAuthController`
  - `TestEventController`
- no full security integration tests that boot Spring Security and verify real `401` and `403` outcomes end to end
- no end-to-end tests with real Postgres, Redis, and Kafka
- no provider integration tests for AWS SES, Twilio, or Firebase

That means the codebase currently has strong unit-level confidence, but more integration confidence can still be added.

## How To Run Tests

## Run all tests

If Maven is installed locally:

```bash
mvn test
```

## Run one test class

```bash
mvn -Dtest=NotificationProcessingServiceTest test
```

## Run one test method

```bash
mvn -Dtest=NotificationProcessingServiceTest#processCreatesNotificationHydratesTemplateAndDispatches test
```

## Run from the IDE

In IntelliJ IDEA or VS Code Java tooling, you can usually:

- open the test class
- click the run icon next to a class or method

This is often the easiest workflow while developing.

## Current Maven Wrapper Limitation

At the moment, the repository contains:

- `mvnw`
- `mvnw.cmd`

but it does not contain the full `.mvn/wrapper/` metadata that the wrapper needs.

Because of that:

- `./mvnw test` may fail

If you see wrapper-related errors, you currently have three practical options:

1. use a local Maven installation and run `mvn test`
2. restore the missing `.mvn/wrapper/` files from a working branch or machine
3. regenerate the wrapper once Maven is available locally

## Why Most Tests Use Mocks

This project integrates with many external systems:

- PostgreSQL
- Redis
- Kafka
- AWS SES
- Twilio
- Firebase

If every test tried to start all of those dependencies, the feedback loop would become slow and fragile.

Mock-based tests give three benefits:

1. they run faster
2. they fail for the class under test, not because Docker or the network is down
3. they make learning the code easier because each test isolates one responsibility

## Reading The Tests As Documentation

The tests are also useful as learning material.

If you are new to the project, good starting tests are:

1. `NotificationProcessingServiceTest`
2. `TemplateControllerTest`
3. `AuthenticatedUserServiceTest`
4. `UnreadCounterCacheTest`

Those four give a strong introduction to:

- the main orchestration flow
- the REST error-handling style
- JWT-based user identity
- Redis cache behavior

## Recommended Next Testing Improvements

If you want to expand the test suite, the highest-value next steps are:

1. add controller tests for device, preference, and notification state endpoints
2. add security integration tests for `401`, `403`, and role-based access
3. add a small set of end-to-end tests using real infrastructure or Testcontainers
4. add contract tests for the dev auth flow and test publish flow

## Practical Advice For New Contributors

When adding a new feature, try to add tests at the level that best matches the change:

- pure business rule
  add a service test
- request validation or error mapping
  add a controller test
- security rule or route protection
  add a Spring Security integration test
- provider-specific integration
  consider an integration test or contract test

That pattern will keep the suite fast while still improving coverage where it matters.

