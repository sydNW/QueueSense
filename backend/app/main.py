import sklearn
from fastapi import FastAPI, HTTPException, Query

from app import model_service, repository
from app.db import get_connection
from app.schemas import ScenarioRequest

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