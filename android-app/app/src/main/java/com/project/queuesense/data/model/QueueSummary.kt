package com.project.queuesense.data.model

data class QueueSummary(
    val patientName: String,
    val waitMinMinutes: Int,
    val waitMaxMinutes: Int,
    val queuePosition: Int,
    val patientsAhead: Int
)