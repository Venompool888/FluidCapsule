-- PostgreSQL is the optional server-side source of truth for synchronized history.
-- The Android app must access this schema through an authenticated HTTPS API,
-- never through an embedded database password or a direct PostgreSQL connection.

CREATE TABLE notification_history (
    event_id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    device_id uuid NOT NULL,
    event_identity text NOT NULL,
    source_package text NOT NULL,
    source_label text NOT NULL,
    title text NOT NULL,
    primary_text text NOT NULL,
    combined_text text NOT NULL,
    posted_at timestamptz NOT NULL,
    captured_at timestamptz NOT NULL,
    removed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, device_id, event_identity)
);

CREATE INDEX notification_history_user_time
    ON notification_history (user_id, captured_at DESC, event_id DESC);

CREATE INDEX notification_history_user_app_time
    ON notification_history (user_id, source_package, captured_at DESC);

CREATE INDEX notification_history_user_device_time
    ON notification_history (user_id, device_id, captured_at DESC);

-- Time ordering:
-- SELECT * FROM notification_history
-- WHERE user_id = $1
-- ORDER BY captured_at DESC, event_id DESC
-- LIMIT $2;

-- App-count ordering with each app folded by the client:
-- SELECT source_package, MAX(source_label) AS source_label,
--        COUNT(*) AS notification_count, MAX(captured_at) AS latest_captured_at
-- FROM notification_history
-- WHERE user_id = $1
-- GROUP BY source_package
-- ORDER BY notification_count DESC, latest_captured_at DESC;
