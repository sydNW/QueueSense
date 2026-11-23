from pydantic import BaseModel, ConfigDict, Field


class ScenarioRequest(BaseModel):
    """A recorded queue situation plus a few optional adjustments.

    The limits are the ranges seen in the historical test data. Values outside
    them would make the model extrapolate beyond anything it has seen.
    Unknown field names are rejected instead of being silently ignored.
    """

    model_config = ConfigDict(extra="forbid")

    base_row_id: int = Field(ge=0, description="Which recorded situation to start from")
    patients_in_line: int | None = Field(default=None, ge=0, le=17)
    avg_wait_last_w1: float | None = Field(default=None, ge=0, le=50)
    avg_wait_last_w2: float | None = Field(default=None, ge=0, le=50)
    avg_wait_last_w3: float | None = Field(default=None, ge=0, le=50)
    start_time_hour: float | None = Field(default=None, ge=7, le=18.3)
    day_of_week: int | None = Field(default=None, ge=2, le=6)


class CheckInRequest(BaseModel):
    full_name: str | None = Field(default=None, max_length=100)
    mobile: str | None = Field(default=None, max_length=20)
    department: str

class ActualWaitRequest(BaseModel):
    actual_wait_min: float = Field(ge=0, le=300)