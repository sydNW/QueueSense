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