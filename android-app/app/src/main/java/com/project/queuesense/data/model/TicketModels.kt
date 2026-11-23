
package com.project.queuesense.data.model

import com.google.gson.annotations.SerializedName

data class CheckInRequest(
    @SerializedName("full_name") val fullName: String,
    val mobile: String?,
    val department: String
)

data class Ticket(
    val id: Long,
    @SerializedName("ticket_code") val ticketCode: String,
    @SerializedName("full_name") val fullName: String,
    val department: String,
    val status: String,
    @SerializedName("checked_in_at") val checkedInAt: String
)

data class PredictionExplanation(
    val feature: String,
    val label: String,
    @SerializedName("current_value") val currentValue: Double,
    @SerializedName("typical_value") val typicalValue: Double,
    @SerializedName("impact_min") val impactMin: Double,
    val effect: String,
    val explanation: String
)

data class Prediction(
    val id: Long,
    @SerializedName("data_source") val dataSource: String,
    @SerializedName("predicted_wait_min") val predictedWaitMin: Double,
    @SerializedName("range_low_min") val rangeLowMin: Double,
    @SerializedName("range_high_min") val rangeHighMin: Double,
    val explanation: List<PredictionExplanation> = emptyList(),
    val notes: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String
)

data class QueueInfo(
    @SerializedName("patients_ahead") val patientsAhead: Int,
    @SerializedName("queue_position") val queuePosition: Int
)

data class TicketResponse(
    val ticket: Ticket,
    val prediction: Prediction,
    val queue: QueueInfo
)
