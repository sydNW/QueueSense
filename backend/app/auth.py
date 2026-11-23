import os
import re
from datetime import datetime, timedelta, timezone
from typing import Optional

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerifyMismatchError
from dotenv import load_dotenv
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app import auth_repository

load_dotenv()

JWT_SECRET = os.environ.get("JWT_SECRET")
if not JWT_SECRET or len(JWT_SECRET) < 32:
    raise RuntimeError("JWT_SECRET must be set in .env (at least 32 characters)")

TOKEN_HOURS = 24 * 7

_hasher = PasswordHasher()
# Used to keep login timing similar when the mobile number is unknown.
_DUMMY_HASH = _hasher.hash("not-a-real-password")
_bearer = HTTPBearer(auto_error=False)

# 07xxxxxxxx, 01xxxxxxxx, 2547xxxxxxxx, +2547xxxxxxxx (and the 01 equivalents)
_MOBILE_RE = re.compile(r"^(?:\+254|254|0)([17]\d{8})$")


def normalize_mobile(raw: str) -> Optional[str]:
    """Return the number as 254XXXXXXXXX, or None if it is not a valid Kenyan mobile."""
    cleaned = re.sub(r"[\s-]", "", raw)
    match = _MOBILE_RE.match(cleaned)
    return f"254{match.group(1)}" if match else None


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password: str, stored_hash: str) -> bool:
    try:
        return _hasher.verify(stored_hash, password)
    except (VerifyMismatchError, InvalidHashError):
        return False


def verify_dummy(password: str) -> None:
    verify_password(password, _DUMMY_HASH)


def create_token(user_id: int) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "iat": now,
        "exp": now + timedelta(hours=TOKEN_HOURS),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")


def get_optional_user(
    creds: Optional[HTTPAuthorizationCredentials] = Depends(_bearer),
) -> Optional[dict]:
    """Return the authenticated user, or None for anonymous requests."""
    if creds is None:
        return None

    try:
        payload = jwt.decode(
            creds.credentials, JWT_SECRET, algorithms=["HS256"]
        )
        user_id = int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    try:
        user = auth_repository.get_user_by_id(user_id)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")

    if user is None:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    return user


def get_current_user(
    user: Optional[dict] = Depends(get_optional_user),
) -> dict:
    """Require an authenticated user."""
    if user is None:
        raise HTTPException(status_code=401, detail="Not authenticated")
    return user