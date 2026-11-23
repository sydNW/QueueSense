
package com.project.queuesense.ui.accuracy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project.queuesense.data.model.AccuracyResponse
import com.project.queuesense.data.model.Prediction
import com.project.queuesense.data.model.QueueInfo
import com.project.queuesense.data.model.Ticket
import com.project.queuesense.data.model.TicketResponse
import com.project.queuesense.ui.theme.QueueSenseTheme

@Composable
fun AccuracyScreen(
    ticket: TicketResponse?,
    accuracy: AccuracyResponse?,
    isLoading: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var actualWaitInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (ticket == null) {
            Text(
                text = "Check in first to compare predictions.",
                style = MaterialTheme.typography.bodyLarge
            )
            return@Column
        }

        Text(
            text = "Compare your predicted waiting time with the actual time you experienced.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading accuracy result...")
            }
            return@Column
        }

        if (accuracy != null) {
            AccuracyResultCard(accuracy)
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Record your actual wait",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Enter how many minutes you actually waited.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = actualWaitInput,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*\\.?\\d*$"))) {
                            actualWaitInput = input
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Actual wait (minutes)") },
                    placeholder = { Text("e.g. 18.5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    enabled = !isSubmitting
                )

                val actualValue = actualWaitInput.toDoubleOrNull()
                val isValid = actualValue != null &&
                        actualValue in 0.0..300.0

                Text(
                    text = "Enter a value between 0 and 300 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        actualValue?.let(onSubmit)
                    },
                    enabled = isValid && !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSubmitting) "Submitting..."
                        else "Log Actual Wait"
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AccuracyResultCard(accuracy: AccuracyResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Prediction Accuracy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            AccuracyInfoRow("Ticket", accuracy.ticketCode)

            AccuracyInfoRow(
                "Predicted",
                "${formatMinutes(accuracy.predictedWaitMin)} min"
            )

            AccuracyInfoRow(
                "Predicted range",
                "${formatMinutes(accuracy.rangeLowMin)}–" +
                        "${formatMinutes(accuracy.rangeHighMin)} min"
            )

            AccuracyInfoRow(
                "Actual",
                "${formatMinutes(accuracy.actualWaitMin)} min"
            )

            AccuracyInfoRow(
                "Source",
                accuracy.actualSource
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
            )

            AccuracyInfoRow(
                "Difference",
                "${formatSignedMinutes(accuracy.differenceMin)} min"
            )

            AccuracyInfoRow(
                "Within predicted range",
                if (accuracy.withinRange) "Yes" else "No"
            )
        }
    }
}

@Composable
private fun AccuracyInfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
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
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatMinutes(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
}

private fun formatSignedMinutes(value: Double): String {
    val formatted = formatMinutes(kotlin.math.abs(value))

    return when {
        value > 0 -> "+$formatted"
        value < 0 -> "-$formatted"
        else -> "0"
    }
}

@Preview(showBackground = true)
@Composable
fun AccuracyScreenEntryPreview() {
    QueueSenseTheme {
        AccuracyScreen(
            ticket = TicketResponse(
                ticket = Ticket(
                    1,
                    "GM-001",
                    "Sample Patient",
                    "General Medicine",
                    "waiting",
                    "2026-11-21T09:12:00+03:00"
                ),
                prediction = Prediction(
                    id = 1,
                    dataSource = "hybrid_live",
                    predictedWaitMin = 17.0,
                    rangeLowMin = 12.0,
                    rangeHighMin = 24.0,
                    createdAt = "2026-11-21T09:38:00+03:00"
                ),
                queue = QueueInfo(6, 7)
            ),
            accuracy = null,
            isLoading = false,
            isSubmitting = false,
            errorMessage = null,
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AccuracyScreenResultPreview() {
    QueueSenseTheme {
        AccuracyScreen(
            ticket = TicketResponse(
                ticket = Ticket(
                    1,
                    "GM-001",
                    "Sample Patient",
                    "General Medicine",
                    "completed",
                    "2026-11-21T09:12:00+03:00"
                ),
                prediction = Prediction(
                    id = 1,
                    dataSource = "hybrid_live",
                    predictedWaitMin = 17.0,
                    rangeLowMin = 12.0,
                    rangeHighMin = 24.0,
                    createdAt = "2026-11-21T09:38:00+03:00"
                ),
                queue = QueueInfo(6, 7)
            ),
            accuracy = AccuracyResponse(
                ticketCode = "GM-001",
                predictedWaitMin = 17.0,
                rangeLowMin = 12.0,
                rangeHighMin = 24.0,
                actualWaitMin = 20.0,
                actualSource = "manual",
                differenceMin = 3.0,
                withinRange = true
            ),
            isLoading = false,
            isSubmitting = false,
            errorMessage = null,
            onSubmit = {}
        )
    }
}
