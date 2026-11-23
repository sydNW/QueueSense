
package com.project.queuesense.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project.queuesense.data.model.Prediction
import com.project.queuesense.data.model.QueueInfo
import com.project.queuesense.data.model.Ticket
import com.project.queuesense.data.model.TicketResponse
import com.project.queuesense.ui.theme.QueueSenseTheme
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class StepState {
    DONE,
    CURRENT,
    UPCOMING
}

@Composable
fun QueueScreen(
    ticket: TicketResponse?,
    onCheckIn: () -> Unit,
    onViewPrediction: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (ticket == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "You are not in a queue yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Check in to see your position and estimated wait.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onCheckIn) {
                Text("Check In")
            }
        }
    } else {
        QueueDetails(ticket, onViewPrediction, modifier)
    }
}

@Composable
private fun QueueDetails(
    ticket: TicketResponse,
    onViewPrediction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waitText = "${ticket.prediction.rangeLowMin.roundToInt()}–" +
            "${ticket.prediction.rangeHighMin.roundToInt()} min"

    val stage = when (ticket.ticket.status) {
        "being_served" -> 2
        "completed" -> 4
        else -> 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = ticket.ticket.department,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Queue ticket ${ticket.ticket.ticketCode}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Stat(
                    "#${ticket.queue.queuePosition}",
                    "Queue position",
                    Modifier.weight(1f)
                )

                Stat(
                    "${ticket.queue.patientsAhead}",
                    "Patients ahead",
                    Modifier.weight(1f)
                )

                Stat(
                    waitText,
                    "Estimated wait",
                    Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Queue progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                ProgressStep(
                    title = "Checked In",
                    subtitle = formatTime(ticket.ticket.checkedInAt),
                    state = StepState.DONE
                )

                ProgressStep(
                    "Waiting",
                    subtitleFor(1, stage),
                    stateFor(1, stage)
                )

                ProgressStep(
                    "Being Served",
                    subtitleFor(2, stage),
                    stateFor(2, stage)
                )

                ProgressStep(
                    "Completed",
                    subtitleFor(3, stage),
                    stateFor(3, stage)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Updated ${formatTime(ticket.prediction.createdAt)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onViewPrediction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Wait Prediction")
        }
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProgressStep(
    title: String,
    subtitle: String,
    state: StepState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .then(
                    when (state) {
                        StepState.UPCOMING ->
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.outline,
                                CircleShape
                            )

                        else ->
                            Modifier.background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                StepState.DONE -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )

                StepState.CURRENT -> Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )

                StepState.UPCOMING -> Unit
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (state == StepState.UPCOMING) {
                    FontWeight.Normal
                } else {
                    FontWeight.Bold
                }
            )

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun stateFor(step: Int, stage: Int): StepState = when {
    step < stage -> StepState.DONE
    step == stage -> StepState.CURRENT
    else -> StepState.UPCOMING
}

private fun subtitleFor(step: Int, stage: Int): String =
    when (stateFor(step, stage)) {
        StepState.DONE -> "Done"
        StepState.CURRENT -> "Current stage"
        StepState.UPCOMING -> "Upcoming"
    }

private fun formatTime(iso: String): String = try {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))
} catch (e: Exception) {
    ""
}

@Preview(showBackground = true)
@Composable
fun QueueScreenPreview() {
    QueueSenseTheme {
        QueueScreen(
            ticket = TicketResponse(
                ticket = Ticket(
                    1,
                    "GM-001",
                    "Sample",
                    "General Medicine",
                    "waiting",
                    "2026-11-17T09:12:00+03:00"
                ),
                prediction = Prediction(
                    id = 1,
                    dataSource = "hybrid_live",
                    predictedWaitMin = 17.0,
                    rangeLowMin = 12.0,
                    rangeHighMin = 24.0,
                    createdAt = "2026-11-21T09:38:00+03:00"
                ),
                queue = QueueInfo(
                    patientsAhead = 6,
                    queuePosition = 7
                )
            ),
            onCheckIn = {},
            onViewPrediction = {}
        )
    }
}
