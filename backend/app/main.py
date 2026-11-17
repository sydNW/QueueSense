import random
import sklearn
from fastapi import FastAPI, HTTPException, Query

from app import model_service, repository
from app.db import get_connection
from app.departments import DEPARTMENTS
from app.schemas import ActualWaitRequest, CheckInRequest, ScenarioRequest


app = FastAPI(title="QueueSense API")


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
        raise HTTPException(status_code=503, detail="Database unavailable")
    return {
        "status": "ok",
        "postgres": version.split(",")[0],
        "queue_tickets": tickets,
        "predictions": predictions,
    }


def _check_row(row_id: int):
    if row_id < 0 or row_id >= model_service.replay_count():
        raise HTTPException(status_code=404, detail="No such replay row")


@app.get("/replay/{row_id}/predict")
def preview_replay(row_id: int):
    """Preview a prediction without saving it."""
    _check_row(row_id)
    return model_service.predict_replay_row(row_id)


@app.post("/replay/{row_id}/predict")
def save_replay_prediction(row_id: int):
    """Predict for a recorded situation and save it to the database."""
    _check_row(row_id)
    result = model_service.predict_replay_row(row_id)
    try:
        return repository.save_prediction(result)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")


@app.get("/predictions")
def recent_predictions(limit: int = Query(20, ge=1, le=100)):
    """List the most recently saved predictions."""
    try:
        return repository.list_predictions(limit)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")




@app.post("/scenarios/predict")
def predict_scenario(req: ScenarioRequest):
    """Demonstration mode: adjust a few inputs of a recorded situation and compare."""
    _check_row(req.base_row_id)
    overrides = req.model_dump(exclude={"base_row_id"}, exclude_none=True)
    result = model_service.predict_manual_scenario(req.base_row_id, overrides)
    try:
        return repository.save_scenario(result)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")




# ---- Patient mode ----

def _ticket_response(ticket: dict, replay_row_id: int, prediction: dict, prediction_id: int, created_at: str):
    return {
        "ticket": ticket,
        "prediction": {
            "id": prediction_id,
            "data_source": "historical_replay",
            "predicted_wait_min": prediction["predicted_wait_min"],
            "range_low_min": prediction["range_low_min"],
            "range_high_min": prediction["range_high_min"],
            "created_at": created_at,
        },
        "queue": model_service.queue_snapshot(replay_row_id),
    }


@app.get("/departments")
def list_departments():
    return list(DEPARTMENTS)


@app.post("/tickets")
def check_in(req: CheckInRequest):
    """Check a patient in. The queue state comes from a recorded situation."""
    name = req.full_name.strip()
    if not name:
        raise HTTPException(status_code=422, detail="Full name is required")
    if req.department not in DEPARTMENTS:
        raise HTTPException(status_code=422, detail="Unknown department")

    replay_row_id = random.randrange(model_service.replay_count())
    prediction = model_service.predict_snapshot(replay_row_id)
    try:
        created = repository.create_ticket(
            name, req.mobile, req.department, DEPARTMENTS[req.department],
            replay_row_id, prediction,
        )
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")

    ticket = {k: created[k] for k in
              ("id", "ticket_code", "full_name", "department", "status", "checked_in_at")}
    return _ticket_response(ticket, replay_row_id, prediction,
                            created["prediction_id"], created["prediction_created_at"])


@app.get("/tickets/{ticket_id}")
def read_ticket(ticket_id: int):
    try:
        found = repository.get_ticket(ticket_id)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")
    if found is None:
        raise HTTPException(status_code=404, detail="No such ticket")
    return _ticket_response(
        found["ticket"], found["replay_row_id"],
        {k: found[k] for k in ("predicted_wait_min", "range_low_min", "range_high_min")},
        found["prediction_id"], found["prediction_created_at"],
    )




def _accuracy_response(row: dict):
    actual = row["actual_wait_min"]
    return {
        "ticket_code": row["ticket_code"],
        "predicted_wait_min": row["predicted_wait_min"],
        "range_low_min": row["range_low_min"],
        "range_high_min": row["range_high_min"],
        "actual_wait_min": actual,
        "actual_source": row["actual_source"],
        "difference_min": round(actual - row["predicted_wait_min"], 1),
        "within_range": row["range_low_min"] <= actual <= row["range_high_min"],
    }


@app.post("/tickets/{ticket_id}/refresh")
def refresh_prediction(ticket_id: int):
    """Move the simulated queue forward to the next recorded situation and re-predict."""
    try:
        found = repository.get_ticket(ticket_id)
        if found is None:
            raise HTTPException(status_code=404, detail="No such ticket")
        if found["ticket"]["status"] in ("completed", "cancelled"):
            raise HTTPException(status_code=409, detail="This ticket is no longer active")
        next_row = (found["replay_row_id"] + 1) % model_service.replay_count()
        prediction = model_service.predict_snapshot(next_row)
        saved = repository.add_prediction(ticket_id, next_row, prediction)
    except HTTPException:
        raise
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")
    return _ticket_response(found["ticket"], next_row, prediction,
                            saved["prediction_id"], saved["created_at"])


@app.post("/tickets/{ticket_id}/actual")
def log_actual_wait(ticket_id: int, req: ActualWaitRequest):
    """Log the actual wait (manual entry) against the prediction made at check-in."""
    try:
        row = repository.get_checkin_prediction(ticket_id)
        if row is None:
            raise HTTPException(status_code=404, detail="No such ticket")
        if row["actual_wait_min"] is not None:
            raise HTTPException(status_code=409, detail="Actual wait already logged")
        if not repository.save_actual_wait(row["prediction_id"], ticket_id, req.actual_wait_min):
            raise HTTPException(status_code=409, detail="Actual wait already logged")
        row = repository.get_checkin_prediction(ticket_id)
    except HTTPException:
        raise
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")
    return _accuracy_response(row)


@app.get("/tickets/{ticket_id}/accuracy")
def ticket_accuracy(ticket_id: int):
    """Predicted vs actual wait for a ticket, once the actual wait has been logged."""
    try:
        row = repository.get_checkin_prediction(ticket_id)
    except Exception as e:
        print("Database error:", repr(e))
        raise HTTPException(status_code=503, detail="Database unavailable")
    if row is None:
        raise HTTPException(status_code=404, detail="No such ticket")
    if row["actual_wait_min"] is None:
        raise HTTPException(status_code=404, detail="No actual wait logged yet")
    return _accuracy_response(row)