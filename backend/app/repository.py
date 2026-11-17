from psycopg.types.json import Jsonb
from app.db import get_connection


def save_prediction(result: dict) -> dict:
    """Store one historical-replay prediction and return it with its database id."""
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
    return {**result, "id": new_id, "created_at": created_at.isoformat()}


def list_predictions(limit: int = 20) -> list[dict]:
    """Return the most recent stored predictions, newest first."""
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
    """Store one manual-scenario prediction (the adjusted one) and return it with its id."""
    adjusted = result["adjusted"]
    inputs = {"base_row_id": result["base_row_id"], "overrides": result["overrides"]}
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
    return {**result, "id": new_id, "created_at": created_at.isoformat()}




def create_ticket(full_name, mobile, department, prefix, replay_row_id, prediction) -> dict:
    """Create a queue ticket and its first prediction in one transaction."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT nextval(pg_get_serial_sequence('queue_tickets', 'id'))")
            ticket_id = cur.fetchone()[0]
            code = f"{prefix}-{ticket_id:03d}"
            cur.execute(
                """
                INSERT INTO queue_tickets (id, ticket_code, full_name, mobile, department)
                VALUES (%s, %s, %s, %s, %s)
                RETURNING checked_in_at
                """,
                (ticket_id, code, full_name, mobile, department),
            )
            checked_in_at = cur.fetchone()[0]
            cur.execute(
                """
                INSERT INTO predictions (
                    ticket_id, data_source, replay_row_id,
                    predicted_wait_min, range_low_min, range_high_min
                )
                VALUES (%s, 'historical_replay', %s, %s, %s, %s)
                RETURNING id, created_at
                """,
                (
                    ticket_id,
                    replay_row_id,
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


def get_ticket(ticket_id: int):
    """Return a ticket with its latest prediction, or None if it does not exist."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT t.id, t.ticket_code, t.full_name, t.department, t.status, t.checked_in_at,
                       p.id, p.replay_row_id, p.predicted_wait_min, p.range_low_min,
                       p.range_high_min, p.created_at
                FROM queue_tickets t
                LEFT JOIN LATERAL (
                    SELECT * FROM predictions WHERE ticket_id = t.id ORDER BY id DESC LIMIT 1
                ) p ON TRUE
                WHERE t.id = %s
                """,
                (ticket_id,),
            )
            r = cur.fetchone()
    if r is None:
        return None
    return {
        "ticket": {
            "id": r[0], "ticket_code": r[1], "full_name": r[2],
            "department": r[3], "status": r[4], "checked_in_at": r[5].isoformat(),
        },
        "prediction_id": r[6],
        "replay_row_id": r[7],
        "predicted_wait_min": float(r[8]) if r[8] is not None else None,
        "range_low_min": float(r[9]) if r[9] is not None else None,
        "range_high_min": float(r[10]) if r[10] is not None else None,
        "prediction_created_at": r[11].isoformat() if r[11] is not None else None,
    }



def add_prediction(ticket_id: int, replay_row_id: int, prediction: dict) -> dict:
    """Store a refreshed prediction for an existing ticket."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO predictions (
                    ticket_id, data_source, replay_row_id,
                    predicted_wait_min, range_low_min, range_high_min
                )
                VALUES (%s, 'historical_replay', %s, %s, %s, %s)
                RETURNING id, created_at
                """,
                (
                    ticket_id,
                    replay_row_id,
                    prediction["predicted_wait_min"],
                    prediction["range_low_min"],
                    prediction["range_high_min"],
                ),
            )
            prediction_id, created_at = cur.fetchone()
    return {"prediction_id": prediction_id, "created_at": created_at.isoformat()}


def get_checkin_prediction(ticket_id: int):
    """The prediction made at check-in (the ticket's first one), or None."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT p.id, t.ticket_code, p.predicted_wait_min, p.range_low_min,
                       p.range_high_min, p.actual_wait_min, p.actual_source, p.actual_logged_at
                FROM predictions p
                JOIN queue_tickets t ON t.id = p.ticket_id
                WHERE p.ticket_id = %s
                ORDER BY p.id ASC
                LIMIT 1
                """,
                (ticket_id,),
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
        "actual_logged_at": r[7].isoformat() if r[7] is not None else None,
    }


def save_actual_wait(prediction_id: int, ticket_id: int, actual_wait_min: float):
    """Record the manually entered actual wait and mark the ticket completed.

    Returns False if an actual wait was already logged for this prediction.
    """
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE predictions
                SET actual_wait_min = %s, actual_source = 'manual_entry', actual_logged_at = now()
                WHERE id = %s AND actual_wait_min IS NULL
                RETURNING id
                """,
                (actual_wait_min, prediction_id),
            )
            if cur.fetchone() is None:
                return False
            cur.execute(
                "UPDATE queue_tickets SET status = 'completed', completed_at = now() WHERE id = %s",
                (ticket_id,),
            )
    return True