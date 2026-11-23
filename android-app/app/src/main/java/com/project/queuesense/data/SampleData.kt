package com.project.queuesense.data

import com.project.queuesense.data.model.QueueSummary

// SAMPLE DATA FOR UI DEVELOPMENT ONLY.
// These numbers are NOT real predictions. They are replaced with real
// backend / model data when the app is connected to the API.
object SampleData {
    val queueSummary = QueueSummary(
        patientName = "Mutua",
        waitMinMinutes = 30,
        waitMaxMinutes = 40,
        queuePosition = 7,
        patientsAhead = 6
    )
    // Placeholder list for the dropdown. Replace with departments from the backend later.
    val departments = listOf("General Medicine", "Pediatrics", "Dental", "Eye Clinic")
}