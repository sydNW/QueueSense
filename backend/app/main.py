from datetime import datetime
from zoneinfo import ZoneInfo

import sklearn
import psycopg
from fastapi import Depends, FastAPI, HTTPException, Query

from app import auth, auth_repository, model_service, repository
from app.auth_schemas import LoginRequest, RegisterRequest
from app.db import get_connection
from app.departments import DEPARTMENTS
from app.schemas import ActualWaitRequest, CheckInRequest, ScenarioRequest

app = FastAPI(title="QueueSense API")

NAIROBI = ZoneInfo("Africa/Nairobi")

# Fields copied from a stored ticket into the prediction response.
# "explanation" is included so reloaded tickets keep their reasons.
_STORED_PREDICTION_FIELDS = (
    "predicted_wait_min",
    "range_low_min",
    "range_high_min",
    "explanation",
)


# ---- Prediction helpers ----

def _current_context_row() -> int:
    """Find a historical row closest to the current time of day."""
    now = datetime.now(NAIROBI)
    current_hour = now.hour + now.minute / 60

    historical_times = model_service._replay_features["StartTime"]

    return int(
        (historical_times - current_hour).abs().idxmin()
    )


def _live_patients_ahead(department: str) -> int:
    """Count patients currently waiting or being served in a department."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM queue_tickets
                WHERE department = %s
                  AND status IN ('waiting', 'being_served')
                """,
                (department,),
            )
            return int(cur.fetchone()[0])


def _ticket_patients_ahead(ticket: dict) -> int:
    """Count active patients in the same department ahead of this ticket."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM queue_tickets
                WHERE department = %s
                  AND status IN ('waiting', 'being_served')
                  AND (
                      checked_in_at < %s
                      OR (
                          checked_in_at = %s
                          AND id < %s
                      )
                  )
                """,
                (
                    ticket["department"],
                    ticket["checked_in_at"],
                    ticket["checked_in_at"],
                    ticket["id"],
                ),
            )
            return int(cur.fetchone()[0])


def _generate_live_prediction(department: str):
    """Combine historical context with the current live queue."""
    row_id = _current_context_row()
    patients_ahead = _live_patients_ahead(department)

    prediction = model_service.predict_snapshot(
        row_id,
        patients_ahead,
    )

    return row_id, patients_ahead, prediction


def _check_row(row_id: int):
    if row_id < 0 or row_id >= model_service.replay_count():
        raise HTTPException(
            status_code=404,
            detail="No such replay row",
        )


# ---- Health ----

@app.get("/health")
def health():
    return {
        "status": "ok",
        "scikit_learn": sklearn.__version__,
        "features": len(model_service.FEATURES),
        "replay_rows": model_service.replay_count(),
    }


@app.get("/health/db")
def health_db():
    try:
        with get_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT version()")
                version = cur.fetchone()[0]

                cur.execute("SELECT count(*) FROM queue_tickets")
                tickets = cur.fetchone()[0]

                cur.execute("SELECT count(*) FROM predictions")
                predictions = cur.fetchone()[0]

    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )

    return {
        "status": "ok",
        "postgres": version.split(",")[0],
        "queue_tickets": tickets,
        "predictions": predictions,
    }


# ---- Historical replay ----

@app.get("/replay/{row_id}/predict")
def preview_replay(row_id: int):
    _check_row(row_id)
    return model_service.predict_replay_row(row_id)


@app.post("/replay/{row_id}/predict")
def save_replay_prediction(row_id: int):
    _check_row(row_id)
    result = model_service.predict_replay_row(row_id)

    try:
        return repository.save_prediction(result)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )


@app.get("/predictions")
def recent_predictions(limit: int = Query(20, ge=1, le=100)):
    try:
        return repository.list_predictions(limit)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )


@app.post("/scenarios/predict")
def predict_scenario(req: ScenarioRequest):
    _check_row(req.base_row_id)

    overrides = req.model_dump(
        exclude={"base_row_id"},
        exclude_none=True,
    )

    result = model_service.predict_manual_scenario(
        req.base_row_id,
        overrides,
    )

    try:
        return repository.save_scenario(result)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )


# ---- Patient mode ----

def _ticket_response(
    ticket: dict,
    prediction: dict,
    prediction_id: int,
    created_at: str,
    patients_ahead: int,
    data_source: str = "hybrid_live",
):
    return {
        "ticket": ticket,
        "prediction": {
            "id": prediction_id,
            "data_source": data_source,
            "predicted_wait_min": prediction["predicted_wait_min"],
            "range_low_min": prediction["range_low_min"],
            "range_high_min": prediction["range_high_min"],
            "created_at": created_at,
            "explanation": prediction.get("explanation", []),
        },
        "queue": {
            "patients_ahead": patients_ahead,
            "queue_position": patients_ahead + 1,
        },
    }


def _ticket_access(user: dict | None):
    if user is not None:
        return {
            "user_id": user["id"],
            "guest_only": False,
        }

    return {
        "user_id": None,
        "guest_only": True,
    }


@app.get("/departments")
def list_departments():
    return list(DEPARTMENTS)


@app.post("/tickets")
def check_in(
    req: CheckInRequest,
    user: dict | None = Depends(auth.get_optional_user),
):
    if user is not None:
        name = user["full_name"]
        mobile = user["mobile"]
        user_id = user["id"]
    else:
        name = (req.full_name or "").strip()
        mobile = req.mobile
        user_id = None

        if not name:
            raise HTTPException(
                status_code=422,
                detail="Full name is required",
            )

    if req.department not in DEPARTMENTS:
        raise HTTPException(
            status_code=422,
            detail="Unknown department",
        )

    try:
        # Return an existing active ticket if the user already has one.
        if user_id is not None:
            existing = repository.get_active_ticket(user_id)

            if existing is not None:
                ahead = _ticket_patients_ahead(
                    existing["ticket"]
                )

                return _ticket_response(
                    existing["ticket"],
                    {
                        k: existing[k]
                        for k in _STORED_PREDICTION_FIELDS
                    },
                    existing["prediction_id"],
                    existing["prediction_created_at"],
                    ahead,
                    existing.get("data_source") or "hybrid_live",
                )

        # Use historical context and the actual live queue.
        replay_row_id, patients_ahead, prediction = (
            _generate_live_prediction(req.department)
        )

        created = repository.create_ticket(
            name,
            mobile,
            req.department,
            DEPARTMENTS[req.department],
            replay_row_id,
            prediction,
            patients_ahead,
            user_id=user_id,
        )

    except HTTPException:
        raise
    except Exception as e:
        print("Check-in error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Unable to complete check-in",
        )

    ticket = {
        k: created[k]
        for k in (
            "id",
            "ticket_code",
            "full_name",
            "department",
            "status",
            "checked_in_at",
        )
    }

    return _ticket_response(
        ticket,
        prediction,
        created["prediction_id"],
        created["prediction_created_at"],
        patients_ahead,
        "hybrid_live",
    )


@app.get("/tickets/current")
def get_current_ticket(
    user: dict = Depends(auth.get_current_user),
):
    try:
        found = repository.get_active_ticket(user["id"])

        if found is None:
            return None

        ahead = _ticket_patients_ahead(found["ticket"])

    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )

    return _ticket_response(
        found["ticket"],
        {
            k: found[k]
            for k in _STORED_PREDICTION_FIELDS
        },
        found["prediction_id"],
        found["prediction_created_at"],
        ahead,
        found.get("data_source") or "hybrid_live",
    )


@app.get("/tickets/{ticket_id}")
def read_ticket(
    ticket_id: int,
    user: dict | None = Depends(auth.get_optional_user),
):
    try:
        found = repository.get_ticket(
            ticket_id,
            **_ticket_access(user),
        )

        if found is None:
            raise HTTPException(
                status_code=404,
                detail="No such ticket",
            )

        ahead = _ticket_patients_ahead(found["ticket"])

    except HTTPException:
        raise
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )

    return _ticket_response(
        found["ticket"],
        {
            k: found[k]
            for k in _STORED_PREDICTION_FIELDS
        },
        found["prediction_id"],
        found["prediction_created_at"],
        ahead,
        found.get("data_source") or "hybrid_live",
    )


# ---- Accuracy ----

def _accuracy_response(row: dict):
    actual = row["actual_wait_min"]

    return {
        "ticket_code": row["ticket_code"],
        "predicted_wait_min": row["predicted_wait_min"],
        "range_low_min": row["range_low_min"],
        "range_high_min": row["range_high_min"],
        "actual_wait_min": actual,
        "actual_source": row["actual_source"],
        "difference_min": round(
            actual - row["predicted_wait_min"],
            1,
        ),
        "within_range": (
            row["range_low_min"]
            <= actual
            <= row["range_high_min"]
        ),
    }


# ---- Refresh prediction ----

@app.post("/tickets/{ticket_id}/refresh")
def refresh_ticket(
    ticket_id: int,
    user: dict | None = Depends(auth.get_optional_user),
):
    try:
        found = repository.get_ticket(
            ticket_id,
            **_ticket_access(user),
        )

        if found is None:
            raise HTTPException(
                status_code=404,
                detail="No such ticket",
            )

        status = found["ticket"]["status"]

        if status in ("completed", "cancelled"):
            raise HTTPException(
                status_code=409,
                detail="Ticket is no longer active",
            )

        # Recalculate from the actual queue, not a simulated next row.
        ahead = _ticket_patients_ahead(found["ticket"])

        replay_row_id = _current_context_row()

        prediction = model_service.predict_snapshot(
            replay_row_id,
            ahead,
        )

        saved = repository.add_prediction(
            ticket_id,
            replay_row_id,
            prediction,
            ahead,
            **_ticket_access(user),
        )

        if saved is None:
            raise HTTPException(
                status_code=404,
                detail="No such active ticket",
            )

    except HTTPException:
        raise
    except Exception as e:
        print("Refresh error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Unable to refresh prediction",
        )

    return _ticket_response(
        found["ticket"],
        prediction,
        saved["prediction_id"],
        saved["created_at"],
        ahead,
        "hybrid_live",
    )


# ---- Actual waiting time ----

@app.post("/tickets/{ticket_id}/actual")
def log_actual_wait(
    ticket_id: int,
    req: ActualWaitRequest,
    user: dict | None = Depends(auth.get_optional_user),
):
    try:
        row = repository.get_checkin_prediction(
            ticket_id,
            **_ticket_access(user),
        )

        if row is None:
            raise HTTPException(
                status_code=404,
                detail="No such ticket",
            )

        if row["actual_wait_min"] is not None:
            raise HTTPException(
                status_code=409,
                detail="Actual wait already logged",
            )

        saved = repository.save_actual_wait(
            row["prediction_id"],
            ticket_id,
            req.actual_wait_min,
            **_ticket_access(user),
        )

        if not saved:
            raise HTTPException(
                status_code=409,
                detail="Actual wait could not be saved",
            )

        row = repository.get_checkin_prediction(
            ticket_id,
            **_ticket_access(user),
        )

    except HTTPException:
        raise
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )

    return _accuracy_response(row)


@app.get("/tickets/{ticket_id}/accuracy")
def ticket_accuracy(
    ticket_id: int,
    user: dict | None = Depends(auth.get_optional_user),
):
    try:
        row = repository.get_checkin_prediction(
            ticket_id,
            **_ticket_access(user),
        )

        if row is None:
            raise HTTPException(
                status_code=404,
                detail="No such ticket",
            )

        if row["actual_wait_min"] is None:
            raise HTTPException(
                status_code=404,
                detail="No actual wait logged yet",
            )

    except HTTPException:
        raise
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )

    return _accuracy_response(row)


# ---- Accounts ----

def _auth_response(user: dict):
    return {
        "token": auth.create_token(user["id"]),
        "user": {
            "id": user["id"],
            "full_name": user["full_name"],
            "mobile": user["mobile"],
        },
    }


@app.post("/auth/register", status_code=201)
def register(req: RegisterRequest):
    name = req.full_name.strip()

    if len(name) < 2:
        raise HTTPException(
            status_code=422,
            detail="Full name is required",
        )

    mobile = auth.normalize_mobile(req.mobile)

    if mobile is None:
        raise HTTPException(
            status_code=422,
            detail="Enter a valid Kenyan mobile number",
        )

    try:
        user = auth_repository.create_user(
            name,
            mobile,
            auth.hash_password(req.password),
        )

    except psycopg.errors.UniqueViolation:
        raise HTTPException(
            status_code=409,
            detail="An account with this mobile number already exists",
        )

    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )

    return _auth_response(user)


@app.post("/auth/login")
def login(req: LoginRequest):
    mobile = auth.normalize_mobile(req.mobile)

    try:
        user = (
            auth_repository.get_user_by_mobile(mobile)
            if mobile
            else None
        )

    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(
            status_code=503,
            detail="Database unavailable",
        )

    if user is None:
        auth.verify_dummy(req.password)
        raise HTTPException(
            status_code=401,
            detail="Invalid mobile number or password",
        )

    if not auth.verify_password(
        req.password,
        user["password_hash"],
    ):
        raise HTTPException(
            status_code=401,
            detail="Invalid mobile number or password",
        )

    return _auth_response(user)


@app.get("/auth/me")
def me(user: dict = Depends(auth.get_current_user)):
    return user