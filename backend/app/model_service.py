import json
from pathlib import Path

import joblib
import pandas as pd

# ---- File paths ----

BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_PATH = BASE_DIR / "models" / "queuesense_rf.joblib"
ERROR_PATH = BASE_DIR / "models" / "error_summary.json"
REPLAY_PATH = BASE_DIR / "data" / "replay_rows.csv"

# ---- Load model and historical data once ----

model = joblib.load(MODEL_PATH)

FEATURES = list(
    model.named_steps["fill_missing"].feature_names_in_
)

with open(ERROR_PATH, encoding="utf-8") as f:
    error_summary = json.load(f)

_replay = pd.read_csv(REPLAY_PATH)
_replay_features = _replay[FEATURES].copy()
_replay_actual = _replay["Wait"]

# Typical feature values used as a comparison baseline.
_feature_baselines = _replay_features.median()


def replay_count() -> int:
    return len(_replay)


def _validate_row(row_id: int):
    if not isinstance(row_id, int) or not 0 <= row_id < replay_count():
        raise ValueError("Invalid historical replay row ID")


def _predict_with_range(x) -> dict:
    """Generate a prediction and its estimated prediction range."""

    predicted = max(
        0.0,
        float(model.predict(x)[0]),
    )

    low = max(
        0.0,
        predicted + error_summary["residual_p10"],
    )

    high = max(
        low,
        predicted + error_summary["residual_p90"],
    )

    return {
        "predicted_wait_min": round(predicted, 1),
        "range_low_min": round(low, 1),
        "range_high_min": round(high, 1),
    }


# ---- Explainable AI ----

# Plain-language names for every model input. Descriptions come from the
# CADENCE dataset's "Contents" sheet.
FEATURE_LABELS = {
    # Patients in line, now and earlier
    "LineCount0": "Patients in line now",
    "LineCount1": "Patients in line 15 minutes ago",
    "LineCount2": "Patients in line 30 minutes ago",
    "LineCount3": "Patients in line 45 minutes ago",
    "LineCount4": "Patients in line 1 hour ago",
    "NoneInLine": "Whether the queue is empty",
    "AheadCount": "Patients scheduled before you today",
    "OutpatientWaitingCount": "Outpatients waiting in line",
    "MalesWaitingCount": "Male patients waiting in line",
    "AvgAgePeopleWaiting": "Average age of patients in line",

    # Waiting and delays building up
    "SumWaits": "Combined waiting time of patients in line",
    "SumDelayWaitingByExamCode": "Total delay among patients in line, by exam type",
    "SumDelayWaitingInLine": "Total delay among patients in line",
    "SumDelayInProgress": "Total delay of exams in progress",

    # Recent waiting times
    "Median5": "Typical wait of the last 5 patients",
    "MostRecent1": "Wait of the most recent patient",
    "MostRecent2": "Wait of the 2nd most recent patient",
    "MostRecent3": "Wait of the 3rd most recent patient",
    "MostRecent4": "Wait of the 4th most recent patient",
    "MostRecent5": "Wait of the 5th most recent patient",
    "AvgWaitLastK1Customers": "Average wait of the last 2 patients",
    "AvgWaitLastK2Customers": "Average wait of the last 4 patients",
    "AvgWaitLastK3Customers": "Average wait of the last 8 patients",
    "AvgWaitLastW1": "Average wait over the last 30 minutes",
    "AvgWaitLastW2": "Average wait over the last hour",
    "AvgWaitLastW3": "Average wait over the last 2 hours",

    # Arrivals and flow through the department
    "FlowCount2": "Patients who started exams in the last 30 minutes",
    "FlowCount4": "Patients who started exams in the last hour",
    "NumCustomersInLastW1": "Patients who arrived in the last 30 minutes",
    "NumCustomersInLastW2": "Patients who arrived in the last hour",
    "NumCustomersInLastW3": "Patients who arrived in the last 2 hours",
    "NumCompletedToday": "Exams completed so far today",
    "NumCompletedInLastW1": "Exams completed in the last 30 minutes",
    "NumCompletedInLastW2": "Exams completed in the last hour",
    "NumCompletedInLastW3": "Exams completed in the last 2 hours",

    # Exams and equipment in use
    "InProgressSize": "Exams in progress",
    "NoneInProgress": "Whether no exams are in progress",
    "NoneCompleted": "Whether no exams are completed yet today",
    "SumInProgress": "Total time current exams have been running",
    "SumTimeToCompleteInProgress": "Expected time to finish exams in progress",
    "NumScannersUsedToday": "Scanners used so far today",

    # Types of exams waiting
    "ThoracicCount": "Patients waiting for chest (thoracic) exams",
    "PediatricCount": "Patients waiting for paediatric exams",
    "MSKCount": "Patients waiting for bone and muscle exams",

    # Schedule and timing
    "BeforeSlot": "Time since the last appointment slot",
    "IsFirst": "Whether this is the first scheduled patient of the day",
    "IsLast": "Whether this is the last scheduled patient of the day",
    "StartTime": "Time of day",
    "DayOfWeek": "Day of the week",
    "Month": "Month of the year",
}

# Features that are yes/no flags (stored as 1 or 0).
_BINARY_FEATURES = {
    "NoneInLine",
    "NoneInProgress",
    "NoneCompleted",
    "IsFirst",
    "IsLast",
}


def _format_value(feature: str, value: float) -> str:
    """Show a feature value the way a patient would read it."""

    value = float(value)

    if feature == "StartTime":
        hours = int(value)
        minutes = int(round((value - hours) * 60))
        if minutes == 60:
            hours += 1
            minutes = 0
        suffix = "am" if hours < 12 else "pm"
        return f"{hours % 12 or 12}:{minutes:02d} {suffix}"

    if feature in _BINARY_FEATURES:
        return "yes" if value >= 0.5 else "no"

    if value.is_integer():
        return str(int(value))

    return f"{value:.1f}"


def _explain_prediction(x, limit: int = 3) -> list:
    """
    Estimate which features influence this individual prediction.

    Each feature is compared with its typical historical value.
    This is a local comparison, not a causal explanation.
    """

    current_prediction = float(model.predict(x)[0])
    impacts = []

    for feature in FEATURES:
        typical_value = _feature_baselines[feature]
        current_value = x.iloc[0][feature]

        if pd.isna(typical_value) or pd.isna(current_value):
            continue

        if float(current_value) == float(typical_value):
            continue

        comparison = x.copy()
        comparison.loc[comparison.index[0], feature] = typical_value

        comparison_prediction = float(model.predict(comparison)[0])

        impact = current_prediction - comparison_prediction

        if abs(impact) < 0.05:
            continue

        label = FEATURE_LABELS.get(
            feature,
            feature.replace("_", " "),
        )

        impacts.append({
            "feature": feature,
            "label": label,
            "current_value": round(float(current_value), 2),
            "typical_value": round(float(typical_value), 2),
            "impact_min": round(impact, 1),
            "effect": (
                "increases"
                if impact > 0
                else "decreases"
            ),
            "explanation": (
                f"Currently {_format_value(feature, current_value)}; "
                f"typically {_format_value(feature, typical_value)}."
            ),
        })

    impacts.sort(
        key=lambda item: abs(item["impact_min"]),
        reverse=True,
    )

    return impacts[:limit]


# ---- Historical replay predictions ----

def predict_replay_row(row_id: int) -> dict:
    """Predict waiting time for a recorded historical situation."""

    _validate_row(row_id)

    x = _replay_features.iloc[[row_id]]
    result = _predict_with_range(x)

    return {
        "row_id": row_id,
        "data_source": "historical_replay",
        **result,
        "actual_wait_min": round(
            float(_replay_actual.iloc[row_id]),
            1,
        ),
    }


# ---- Demonstration mode ----

FIELD_TO_FEATURE = {
    "patients_in_line": "LineCount0",
    "avg_wait_last_w1": "AvgWaitLastW1",
    "avg_wait_last_w2": "AvgWaitLastW2",
    "avg_wait_last_w3": "AvgWaitLastW3",
    "start_time_hour": "StartTime",
    "day_of_week": "DayOfWeek",
}


def predict_manual_scenario(
    base_row_id: int,
    overrides: dict,
) -> dict:
    """Compare a historical prediction with an adjusted scenario."""

    _validate_row(base_row_id)

    base_x = _replay_features.iloc[[base_row_id]].copy()
    adjusted_x = base_x.copy()

    for field, value in overrides.items():
        if field not in FIELD_TO_FEATURE:
            raise ValueError(f"Unknown scenario field: {field}")

        adjusted_x.loc[
            adjusted_x.index[0],
            FIELD_TO_FEATURE[field],
        ] = float(value)

    if "patients_in_line" in overrides:
        patients = int(overrides["patients_in_line"])

        adjusted_x.loc[
            adjusted_x.index[0],
            "NoneInLine",
        ] = 1.0 if patients == 0 else 0.0

    base = _predict_with_range(base_x)
    adjusted = _predict_with_range(adjusted_x)

    base["recorded_actual_wait_min"] = round(
        float(_replay_actual.iloc[base_row_id]),
        1,
    )

    return {
        "data_source": "manual_scenario",
        "base_row_id": base_row_id,
        "overrides": overrides,
        "base": base,
        "adjusted": adjusted,
        "change_min": round(
            adjusted["predicted_wait_min"]
            - base["predicted_wait_min"],
            1,
        ),
    }


# ---- Historical queue context ----

def queue_snapshot(row_id: int) -> dict:
    """Return the queue size recorded in a historical row."""

    _validate_row(row_id)

    in_line = int(
        _replay_features.iloc[row_id]["LineCount0"]
    )

    return {
        "patients_ahead": in_line,
        "queue_position": in_line + 1,
    }


# ---- Live queue predictions ----

def predict_snapshot(
    row_id: int,
    patients_ahead: int | None = None,
) -> dict:
    """
    Predict waiting time using historical model context
    and the current QueueSense queue size.
    """

    _validate_row(row_id)

    x = _replay_features.iloc[[row_id]].copy()

    if patients_ahead is not None:
        if patients_ahead < 0:
            raise ValueError(
                "Patients ahead cannot be negative"
            )

        x.loc[
            x.index[0],
            "LineCount0",
        ] = float(patients_ahead)

        x.loc[
            x.index[0],
            "NoneInLine",
        ] = 1.0 if patients_ahead == 0 else 0.0

    result = _predict_with_range(x)

    return {
        **result,
        "patients_ahead": patients_ahead,
        "explanation": _explain_prediction(x),
    }


# ---- Global feature importance ----

def get_feature_importance(limit: int = 5) -> list:
    """Return the most influential features across the trained model."""

    forest = model.named_steps["forest"]

    importance = forest.feature_importances_

    ranked = sorted(
        zip(FEATURES, importance),
        key=lambda item: item[1],
        reverse=True,
    )

    return [
        {
            "feature": feature,
            "importance": round(float(score), 4),
        }
        for feature, score in ranked[:limit]
    ]