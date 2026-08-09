# PostgreSQL notification-history boundary

## Decision

Use PostgreSQL, not ClickHouse, if notification history is synchronized to a server.

PostgreSQL fits the primary-record workload because history needs transactional writes, per-user and per-device isolation, lifecycle upserts, pagination, future deletion, and predictable point queries. ClickHouse remains an optional downstream analytics store only if anonymized aggregate volume eventually justifies it.

## Storage layers

- Android SQLite is the offline source used by the current app. Recording and browsing continue to work without a network.
- PostgreSQL is the future synchronized source of truth across devices.
- The Android app must never contain PostgreSQL credentials or connect to PostgreSQL directly.
- A future authenticated HTTPS API owns authorization, upload idempotency, pagination, retention, and deletion.

The initial schema is in [`postgres/notification_history.sql`](postgres/notification_history.sql).

## Sync prerequisites not yet implemented

Before notification bodies can leave the device, the project still needs:

1. an authenticated user and device identity model;
2. an HTTPS API with per-user authorization and rate limiting;
3. explicit opt-in separate from the local recording switch;
4. encryption and a documented retention/deletion policy;
5. an offline upload queue with idempotent event identities;
6. conflict handling for notification lifecycle updates;
7. redaction rules for OTPs and other sensitive fields.

Until those prerequisites exist, notification content remains in the local SQLite database and the manifest continues to omit internet permission.
