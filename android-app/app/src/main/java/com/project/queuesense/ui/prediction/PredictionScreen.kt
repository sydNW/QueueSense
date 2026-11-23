package com.project.queuesense.ui.prediction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project.queuesense.data.model.Prediction
import com.project.queuesense.data.model.PredictionExplanation
import com.project.queuesense.data.model.QueueInfo
import com.project.queuesense.data.model.Ticket
import com.project.queuesense.data.model.TicketResponse
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Model inputs that are yes/no flags (stored as 1 or 0).
private val BINARY_FEATURES = setOf(
    "NoneInLine",
    "NoneInProgress",
    "NoneCompleted",
    "IsFirst",
    "IsLast"
)

// Plain-language names for the factors patients are most likely to see.
// Anything not listed here falls back to the label sent by the server.
private val PATIENT_LABELS = mapOf(
    "StartTime" to "Time of day",
    "LineCount0" to "People waiting ahead of you",
    "NoneInLine" to "Nobody else waiting",
    "SumDelayWaitingByExamCode" to "Delays among people already waiting",
    "NumScannersUsedToday" to "Scanners in use today"
)

@Composable
fun PredictionScreen(
    ticket: TicketResponse?,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (ticket == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No active prediction",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Join a queue to receive your estimated waiting time.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        PredictionDetails(
            ticket = ticket,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
            onRefresh = onRefresh,
            modifier = modifier
        )
    }
}

@Composable
private fun PredictionDetails(
    ticket: TicketResponse,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prediction = ticket.prediction

    // Gson may return null when these fields are absent from the response.
    val explanations = prediction.explanation.orEmpty()
    val notes = prediction.notes.orEmpty()

    val low = prediction.rangeLowMin.roundToInt()
    val high = prediction.rangeHighMin.roundToInt()
    val estimate = prediction.predictedWaitMin.roundToInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "YOUR ESTIMATED WAIT",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$low–$high min",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                InfoRow("Most likely", "$estimate min")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Your queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow("Ticket", ticket.ticket.ticketCode)
                InfoRow("Department", ticket.ticket.department)
                InfoRow("Your place in line", ticket.queue.queuePosition.toString())
                InfoRow("People ahead of you", ticket.queue.patientsAhead.toString())
            }
        }

        if (explanations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            WhyCard(explanations)
        }

        if (notes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Please note",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    notes.forEach { note ->
                        Text(
                            text = "• $note",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (ticket.ticket.status != "completed") {
            Button(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isRefreshing) {
                        "Updating..."
                    } else {
                        "Update my estimate"
                    }
                )
            }
        }
    }
}

@Composable
private fun WhyCard(explanations: List<PredictionExplanation>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Why this wait time?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "What is making your wait longer or shorter than a usual day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            explanations.forEachIndexed { index, reason ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                ExplanationItem(reason)
            }
        }
    }
}

@Composable
private fun ExplanationItem(reason: PredictionExplanation) {
    val increases = reason.effect == "increases"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = patientLabel(reason.label, reason.feature),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Right now: ${formatValue(reason.feature, reason.currentValue)}" +
                        "  ·  Usually: ${formatValue(reason.feature, reason.typicalValue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = if (increases) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Text(
                text = "${oneDecimal(abs(reason.impactMin))} min " +
                        if (increases) "longer" else "shorter",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (increases) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// Picks the wording a patient sees for a factor: our plain-language name first,
// then the server's label, and finally the raw column name split into words
// (older stored predictions may carry the raw name).
private fun patientLabel(label: String, feature: String): String {
    PATIENT_LABELS[feature]?.let { return it }

    return if (label == feature) {
        label.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
    } else {
        label
    }
}

private fun oneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value)

// Shows a model input the way a patient would read it.
private fun formatValue(feature: String, value: Double): String {
    if (feature == "StartTime") {
        var hours = value.toInt()
        var minutes = ((value - hours) * 60).roundToInt()

        if (minutes == 60) {
            hours += 1
            minutes = 0
        }

        val suffix = if (hours < 12) "am" else "pm"
        val hour12 = if (hours % 12 == 0) 12 else hours % 12

        return String.format(Locale.US, "%d:%02d %s", hour12, minutes, suffix)
    }

    if (feature in BINARY_FEATURES) {
        return if (value >= 0.5) "yes" else "no"
    }

    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        oneDecimal(value)
    }
}

@Preview(showBackground = true)
@Composable
private fun PredictionScreenPreview() {
    val sampleTicket = TicketResponse(
        ticket = Ticket(
            id = 1,
            ticketCode = "QS-001",
            fullName = "Sample Patient",
            department = "General",
            status = "waiting",
            checkedInAt = "2026-11-17T09:00:00+03:00"
        ),
        prediction = Prediction(
            id = 1,
            dataSource = "hybrid_live",
            predictedWaitMin = 17.0,
            rangeLowMin = 12.0,
            rangeHighMin = 24.0,
            explanation = listOf(
                PredictionExplanation(
                    feature = "StartTime",
                    label = "Time of day",
                    currentValue = 16.78,
                    typicalValue = 12.0,
                    impactMin = 2.9,
                    effect = "increases",
                    explanation = ""
                ),
                PredictionExplanation(
                    feature = "SumDelayWaitingByExamCode",
                    label = "Total delay among patients in line, by exam type",
                    currentValue = 120.0,
                    typicalValue = 340.0,
                    impactMin = -1.4,
                    effect = "decreases",
                    explanation = ""
                )
            ),
            notes = emptyList(),
            createdAt = "2026-11-17T09:38:00+03:00"
        ),
        queue = QueueInfo(
            patientsAhead = 3,
            queuePosition = 4
        )
    )

    MaterialTheme {
        PredictionScreen(
            ticket = sampleTicket,
            isRefreshing = false,
            errorMessage = null,
            onRefresh = {}
        )
    }
}