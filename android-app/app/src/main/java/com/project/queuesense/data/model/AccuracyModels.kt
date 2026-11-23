
package com.project.queuesense.data.model

import com.google.gson.annotations.SerializedName

data class ActualWaitRequest(
    @SerializedName("actual_wait_min")
    val actualWaitMin: Double
)

data class AccuracyResponse(
    @SerializedName("ticket_code")
    val ticketCode: String,

    @SerializedName("predicted_wait_min")
    val predictedWaitMin: Double,

    @SerializedName("range_low_min")
    val rangeLowMin: Double,

    @SerializedName("range_high_min")
    val rangeHighMin: Double,

    @SerializedName("actual_wait_min")
    val actualWaitMin: Double,

    @SerializedName("actual_source")
    val actualSource: String,

    @SerializedName("difference_min")
    val differenceMin: Double,

    @SerializedName("within_range")
    val withinRange: Boolean
)
