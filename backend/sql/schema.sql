CREATE TABLE queue_tickets (
    id             BIGSERIAL PRIMARY KEY,
    ticket_code    TEXT NOT NULL UNIQUE,
    full_name      TEXT NOT NULL,
    mobile         TEXT,
    department     TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'waiting'
                   CHECK (status IN ('waiting', 'being_served', 'completed', 'cancelled')),
    checked_in_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ
);

CREATE TABLE predictions (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT REFERENCES queue_tickets(id) ON DELETE SET NULL,
    data_source         TEXT NOT NULL
                        CHECK (data_source IN ('historical_replay', 'manual_scenario')),
    replay_row_id       INTEGER,
    inputs              JSONB,
    predicted_wait_min  NUMERIC(6,1) NOT NULL,
    range_low_min       NUMERIC(6,1) NOT NULL,
    range_high_min      NUMERIC(6,1) NOT NULL,
    model_version       TEXT NOT NULL DEFAULT 'rf-v1',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actual_wait_min     NUMERIC(6,1),
    actual_source       TEXT CHECK (actual_source IN ('recorded', 'manual_entry')),
    actual_logged_at    TIMESTAMPTZ
);

CREATE INDEX idx_predictions_ticket ON predictions (ticket_id);