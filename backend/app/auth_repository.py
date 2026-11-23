from typing import Optional

from app.db import get_connection


def create_user(full_name: str, mobile: str, password_hash: str) -> dict:
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO users (full_name, mobile, password_hash)
                VALUES (%s, %s, %s)
                RETURNING id
                """,
                (full_name, mobile, password_hash),
            )
            user_id = cur.fetchone()[0]
    return {"id": user_id, "full_name": full_name, "mobile": mobile}


def get_user_by_mobile(mobile: str) -> Optional[dict]:
    """Includes the password hash. Only for use inside login."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id, full_name, mobile, password_hash FROM users WHERE mobile = %s",
                (mobile,),
            )
            r = cur.fetchone()
    if r is None:
        return None
    return {"id": r[0], "full_name": r[1], "mobile": r[2], "password_hash": r[3]}


def get_user_by_id(user_id: int) -> Optional[dict]:
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id, full_name, mobile FROM users WHERE id = %s",
                (user_id,),
            )
            r = cur.fetchone()
    if r is None:
        return None
    return {"id": r[0], "full_name": r[1], "mobile": r[2]}