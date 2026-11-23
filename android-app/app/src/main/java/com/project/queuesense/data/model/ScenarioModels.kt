package com.project.queuesense.data.model

import com.google.gson.annotations.SerializedName

data class ScenarioRequest(
    @SerializedName("base_row_id")
    val baseRowId: Int,

    @SerializedName("patients_in_line")
    val patientsInLine: Int? = null,

    @SerializedName("avg_wait_last_w1")
    val avgWaitLastW1: Double? = null,

    @SerializedName("avg_wait_last_w2")
    val avgWaitLastW2: Double? = null,

    @SerializedName("avg_wait_last_w3")
    val avgWaitLastW3: Double? = null,

    @SerializedName("start_time_hour")
    val startTimeHour: Double? = null,

    @SerializedName("day_of_week")
    val dayOfWeek: Int? = null
)

data class ScenarioPrediction(
    @SerializedName("predicted_wait_min")
    val predictedWaitMin: Double,

    @SerializedName("range_low_min")
    val rangeLowMin: Double,

    @SerializedName("range_high_min")
    val rangeHighMin: Double,

    @SerializedName("recorded_actual_wait_min")
    val recordedActualWaitMin: Double? = null
)

data class ScenarioResponse(
    @SerializedName("data_source")
    val dataSource: String,

    @SerializedName("base_row_id")
    val baseRowId: Int,

    val overrides: Map<String, Double>,

    val base: ScenarioPrediction,

    val adjusted: ScenarioPrediction,

    @SerializedName("change_min")
    val changeMin: Double,

    val id: Long,

    @SerializedName("created_at")
    val createdAt: String
)