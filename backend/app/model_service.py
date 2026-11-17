import json
from pathlib import Path

import joblib
import pandas as pd

# backend/ folder (two levels up from this file)
BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_PATH = BASE_DIR / "models" / "queuesense_rf.joblib"
ERROR_PATH = BASE_DIR / "models" / "error_summary.json"
REPLAY_PATH = BASE_DIR / "data" / "replay_rows.csv"

# Loaded once when the server starts.
model = joblib.load(MODEL_PATH)
FEATURES = list(model.named_steps["fill_missing"].feature_names_in_)

with open(ERROR_PATH) as f:
    error_summary = json.load(f)

_replay = pd.read_csv(REPLAY_PATH)
_replay_features = _replay[FEATURES]
_replay_actual = _replay["Wait"]


def replay_count() -> int:
    return len(_replay)


def predict_replay_row(row_id: int) -> dict:
    """Predict the wait for one recorded queue situation (test period only)."""
    x = _replay_features.iloc[[row_id]]
    predicted = max(0.0, float(model.predict(x)[0]))
    low = max(0.0, predicted + error_summary["residual_p10"])
    high = predicted + error_summary["residual_p90"]
    return {
        "row_id": row_id,
        "data_source": "historical_replay",
        "predicted_wait_min": round(predicted, 1),
        "range_low_min": round(low, 1),
        "range_high_min": round(high, 1),
        "actual_wait_min": round(float(_replay_actual.iloc[row_id]), 1),
    }



# ---- Demonstration mode: adjust a few inputs of a recorded situation ----

# Friendly request field -> real model column.
FIELD_TO_FEATURE = {
    "patients_in_line": "LineCount0",
    "avg_wait_last_w1": "AvgWaitLastW1",
    "avg_wait_last_w2": "AvgWaitLastW2",
    "avg_wait_last_w3": "AvgWaitLastW3",
    "start_time_hour": "StartTime",
    "day_of_week": "DayOfWeek",
}


def _predict_with_range(x) -> dict:
    predicted = max(0.0, float(model.predict(x)[0]))
    return {
        "predicted_wait_min": round(predicted, 1),
        "range_low_min": round(max(0.0, predicted + error_summary["residual_p10"]), 1),
        "range_high_min": round(predicted + error_summary["residual_p90"], 1),
    }


def predict_manual_scenario(base_row_id: int, overrides: dict) -> dict:
    """Predict for a recorded situation, then again with the adjusted inputs.

    Every column the user did not adjust keeps its real recorded value.
    """
    base_x = _replay_features.iloc[[base_row_id]].copy()
    adjusted_x = base_x.copy()

    for field, value in overrides.items():
        adjusted_x[FIELD_TO_FEATURE[field]] = float(value)
    if "patients_in_line" in overrides:
        # The flag must agree with the line length (verified in the data: 100% match).
        adjusted_x["NoneInLine"] = 1.0 if overrides["patients_in_line"] == 0 else 0.0

    base = _predict_with_range(base_x)
    adjusted = _predict_with_range(adjusted_x)
    base["recorded_actual_wait_min"] = round(float(_replay_actual.iloc[base_row_id]), 1)

    return {
        "data_source": "manual_scenario",
        "base_row_id": base_row_id,
        "overrides": overrides,
        "base": base,
        "adjusted": adjusted,
        "change_min": round(adjusted["predicted_wait_min"] - base["predicted_wait_min"], 1),
    }




# ---- Patient mode: queue state comes from a recorded situation ----

def queue_snapshot(row_id: int) -> dict:
    """Queue size at the moment of a recorded situation (real LineCount0 value)."""
    in_line = int(_replay_features.iloc[row_id]["LineCount0"])
    return {"patients_ahead": in_line, "queue_position": in_line + 1}


def predict_snapshot(row_id: int) -> dict:
    """Model prediction for a recorded situation, without its recorded actual wait."""
    x = _replay_features.iloc[[row_id]]
    return _predict_with_range(x)