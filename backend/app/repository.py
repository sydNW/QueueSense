from psycopg.types.json import Jsonb

from app.db import get_connection


def save_prediction(result: dict) -> dict:
    """Store one historical-replay prediction."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO predictions (
                    data_source, replay_row_id,
                    predicted_wait_min, range_low_min, range_high_min,
                    actual_wait_min, actual_source, actual_logged_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, 'recorded', now())
                RETURNING id, created_at
                """,
                (
                    result["data_source"],
                    result["row_id"],
                    result["predicted_wait_min"],
                    result["range_low_min"],
                    result["range_high_min"],
                    result["actual_wait_min"],
                ),
            )
            new_id, created_at = cur.fetchone()

    return {
        **result,
        "id": new_id,
        "created_at": created_at.isoformat(),
    }


def list_predictions(limit: int = 20) -> list[dict]:
    """Return the most recent stored predictions."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, data_source, replay_row_id, predicted_wait_min,
                       range_low_min, range_high_min, actual_wait_min, created_at
                FROM predictions
                ORDER BY id DESC
                LIMIT %s
                """,
                (limit,),
            )
            rows = cur.fetchall()

    return [
        {
            "id": r[0],
            "data_source": r[1],
            "replay_row_id": r[2],
            "predicted_wait_min": float(r[3]),
            "range_low_min": float(r[4]),
            "range_high_min": float(r[5]),
            "actual_wait_min": float(r[6]) if r[6] is not None else None,
            "created_at": r[7].isoformat(),
        }
        for r in rows
    ]


def save_scenario(result: dict) -> dict:
    """Store one manual-scenario prediction."""
    adjusted = result["adjusted"]

    inputs = {
        "base_row_id": result["base_row_id"],
        "overrides": result["overrides"],
    }

    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO predictions (
                    data_source, replay_row_id, inputs,
                    predicted_wait_min, range_low_min, range_high_min
                )
                VALUES ('manual_scenario', %s, %s, %s, %s, %s)
                RETURNING id, created_at
                """,
                (
                    result["base_row_id"],
                    Jsonb(inputs),
                    adjusted["predicted_wait_min"],
                    adjusted["range_low_min"],
                    adjusted["range_high_min"],
                ),
            )
            new_id, created_at = cur.fetchone()

    return {
        **result,
        "id": new_id,
        "created_at": created_at.isoformat(),
    }


def create_ticket(
    full_name,
    mobile,
    department,
    prefix,
    replay_row_id,
    prediction,
    patients_ahead,
    user_id=None,
) -> dict:
    """Create a ticket and its initial hybrid prediction."""
    # The explanation is stored with the prediction so it can be shown
    # again whenever the ticket is reloaded.
    inputs = {
        "patients_ahead": patients_ahead,
        "prediction_mode": "hybrid_live_queue",
        "historical_context_row": replay_row_id,
        "explanation": prediction.get("explanation", []),
    }

    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT nextval(pg_get_serial_sequence('queue_tickets', 'id'))"
            )
            ticket_id = cur.fetchone()[0]

            code = f"{prefix}-{ticket_id:03d}"

            cur.execute(
                """
                INSERT INTO queue_tickets (
                    id, ticket_code, full_name, mobile, department, user_id
                )
                VALUES (%s, %s, %s, %s, %s, %s)
                RETURNING checked_in_at
                """,
                (
                    ticket_id,
                    code,
                    full_name,
                    mobile,
                    department,
                    user_id,
                ),
            )

            checked_in_at = cur.fetchone()[0]

            cur.execute(
                """
                INSERT INTO predictions (
                    ticket_id, data_source, replay_row_id, inputs,
                    predicted_wait_min, range_low_min, range_high_min
                )
                VALUES (%s, 'hybrid_live', %s, %s, %s, %s, %s)
                RETURNING id, created_at
                """,
                (
                    ticket_id,
                    replay_row_id,
                    Jsonb(inputs),
                    prediction["predicted_wait_min"],
                    prediction["range_low_min"],
                    prediction["range_high_min"],
                ),
            )

            prediction_id, prediction_at = cur.fetchone()

    return {
        "id": ticket_id,
        "ticket_code": code,
        "full_name": full_name,
        "department": department,
        "status": "waiting",
        "checked_in_at": checked_in_at.isoformat(),
        "prediction_id": prediction_id,
        "prediction_created_at": prediction_at.isoformat(),
    }


def get_ticket(
    ticket_id: int,
    user_id: int | None = None,
    guest_only: bool = False,
):
    """Return a ticket only if it belongs to the requester."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT
                    t.id, t.ticket_code, t.full_name, t.department,
                    t.status, t.checked_in_at,
                    p.id, p.replay_row_id, p.predicted_wait_min,
                    p.range_low_min, p.range_high_min, p.created_at,
                    p.inputs, p.data_source
                FROM queue_tickets t
                LEFT JOIN LATERAL (
                    SELECT *
                    FROM predictions
                    WHERE ticket_id = t.id
                    ORDER BY id DESC
                    LIMIT 1
                ) p ON TRUE
                WHERE t.id = %s
                  AND (
                      (%s IS NOT NULL AND t.user_id = %s)
                      OR
                      (%s IS NULL AND %s = TRUE AND t.user_id IS NULL)
                  )
                """,
                (ticket_id, user_id, user_id, user_id, guest_only),
            )

            r = cur.fetchone()

    if r is None:
        return None

    inputs = r[12] or {}

    return {
        "ticket": {
            "id": r[0],
            "ticket_code": r[1],
            "full_name": r[2],
            "department": r[3],
            "status": r[4],
            "checked_in_at": r[5].isoformat(),
        },
        "prediction_id": r[6],
        "replay_row_id": r[7],
        "predicted_wait_min": float(r[8]) if r[8] is not None else None,
        "range_low_min": float(r[9]) if r[9] is not None else None,
        "range_high_min": float(r[10]) if r[10] is not None else None,
        "prediction_created_at": (
            r[11].isoformat() if r[11] is not None else None
        ),
        "patients_ahead": inputs.get("patients_ahead"),
        "prediction_mode": inputs.get(
            "prediction_mode",
            "historical_replay",
        ),
        # Tickets created before this change have no stored explanation.
        "explanation": inputs.get("explanation", []),
        "data_source": r[13],
    }


def add_prediction(
    ticket_id: int,
    replay_row_id: int,
    prediction: dict,
    patients_ahead: int,
    user_id: int | None = None,
    guest_only: bool = False,
):
    """Save a refreshed prediction for an authorized active ticket."""

    inputs = {
        "patients_ahead": patients_ahead,
        "prediction_mode": "hybrid_live_queue",
        "historical_context_row": replay_row_id,
        "explanation": prediction.get("explanation", []),
    }

    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO predictions (
                    ticket_id, data_source, replay_row_id, inputs,
                    predicted_wait_min, range_low_min, range_high_min
                )
                SELECT
                    t.id, 'hybrid_live', %s, %s, %s, %s, %s
                FROM queue_tickets t
                WHERE t.id = %s
                  AND t.status IN ('waiting', 'being_served')
                  AND (
                      (%s IS NOT NULL AND t.user_id = %s)
                      OR
                      (%s IS NULL AND %s = TRUE AND t.user_id IS NULL)
                  )
                RETURNING id, created_at
                """,
                (
                    replay_row_id,
                    Jsonb(inputs),
                    prediction["predicted_wait_min"],
                    prediction["range_low_min"],
                    prediction["range_high_min"],
                    ticket_id,
                    user_id,
                    user_id,
                    user_id,
                    guest_only,
                ),
            )

            row = cur.fetchone()

    if row is None:
        return None

    return {
        "prediction_id": row[0],
        "created_at": row[1].isoformat(),
    }


def get_checkin_prediction(
    ticket_id: int,
    user_id: int | None = None,
    guest_only: bool = False,
):
    """Return the initial prediction for the authorized ticket."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT
                    p.id, t.ticket_code, p.predicted_wait_min,
                    p.range_low_min, p.range_high_min,
                    p.actual_wait_min, p.actual_source, p.actual_logged_at
                FROM predictions p
                JOIN queue_tickets t ON t.id = p.ticket_id
                WHERE p.ticket_id = %s
                  AND (
                      (%s IS NOT NULL AND t.user_id = %s)
                      OR
                      (%s IS NULL AND %s = TRUE AND t.user_id IS NULL)
                  )
                ORDER BY p.id ASC
                LIMIT 1
                """,
                (ticket_id, user_id, user_id, user_id, guest_only),
            )

            r = cur.fetchone()

    if r is None:
        return None

    return {
        "prediction_id": r[0],
        "ticket_code": r[1],
        "predicted_wait_min": float(r[2]),
        "range_low_min": float(r[3]),
        "range_high_min": float(r[4]),
        "actual_wait_min": float(r[5]) if r[5] is not None else None,
        "actual_source": r[6],
        "actual_logged_at": (
            r[7].isoformat() if r[7] is not None else None
        ),
    }


def save_actual_wait(
    prediction_id: int,
    ticket_id: int,
    actual_wait_min: float,
    user_id: int | None = None,
    guest_only: bool = False,
):
    """Save actual wait and complete the authorized ticket."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE predictions p
                SET actual_wait_min = %s,
                    actual_source = 'manual_entry',
                    actual_logged_at = now()
                FROM queue_tickets t
                WHERE p.id = %s
                  AND p.ticket_id = t.id
                  AND t.id = %s
                  AND p.actual_wait_min IS NULL
                  AND (
                      (%s IS NOT NULL AND t.user_id = %s)
                      OR
                      (%s IS NULL AND %s = TRUE AND t.user_id IS NULL)
                  )
                RETURNING p.id
                """,
                (
                    actual_wait_min,
                    prediction_id,
                    ticket_id,
                    user_id,
                    user_id,
                    user_id,
                    guest_only,
                ),
            )

            if cur.fetchone() is None:
                return False

            cur.execute(
                """
                UPDATE queue_tickets
                SET status = 'completed',
                    completed_at = now()
                WHERE id = %s
                """,
                (ticket_id,),
            )

    return True


def set_ticket_status(
    ticket_id: int,
    status: str,
    user_id: int | None = None,
    guest_only: bool = False,
) -> bool:
    """Update status only when the requester owns the ticket."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE queue_tickets
                SET status = %s
                WHERE id = %s
                  AND (
                      (%s IS NOT NULL AND user_id = %s)
                      OR
                      (%s IS NULL AND %s = TRUE AND user_id IS NULL)
                  )
                RETURNING id
                """,
                (
                    status,
                    ticket_id,
                    user_id,
                    user_id,
                    user_id,
                    guest_only,
                ),
            )

            return cur.fetchone() is not None


def get_active_ticket(user_id: int):
    """Return the logged-in user's latest active ticket."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id
                FROM queue_tickets
                WHERE user_id = %s
                  AND status IN ('waiting', 'being_served')
                ORDER BY checked_in_at DESC
                LIMIT 1
                """,
                (user_id,),
            )

            row = cur.fetchone()

    if row is None:
        return None

    return get_ticket(row[0], user_id=user_id)