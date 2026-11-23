
package com.project.queuesense.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project.queuesense.data.model.Prediction
import com.project.queuesense.data.model.QueueInfo
import com.project.queuesense.data.model.Ticket
import com.project.queuesense.data.model.TicketResponse
import com.project.queuesense.ui.theme.QueueSenseTheme
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    ticket: TicketResponse?,
    onCheckIn: () -> Unit,
    onViewQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstName = ticket?.ticket?.fullName?.trim()?.substringBefore(" ")

    val waitText = if (ticket != null) {
        "${ticket.prediction.rangeLowMin.roundToInt()}–${ticket.prediction.rangeHighMin.roundToInt()} min"
    } else {
        "—"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = greeting(firstName),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (ticket != null) {
                "Here is your queue summary for today"
            } else {
                "You are not in a queue yet"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ESTIMATED WAITING TIME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About this estimate",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = waitText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatBlock(
                        value = ticket?.let { "#${it.queue.queuePosition}" } ?: "—",
                        label = "Queue position",
                        modifier = Modifier.weight(1f)
                    )

                    StatBlock(
                        value = ticket?.queue?.patientsAhead?.toString() ?: "—",
                        label = "Patients ahead",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCheckIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Check In")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onViewQueue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Queue")
        }
    }
}

@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun greeting(name: String?): String {
    val hour = LocalTime.now().hour

    val part = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    return if (name.isNullOrBlank()) part else "$part, $name"
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    QueueSenseTheme {
        HomeScreen(
            ticket = TicketResponse(
                ticket = Ticket(
                    1,
                    "GM-001",
                    "Sample Patient",
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
                    createdAt = "2026-11-17T09:38:00+03:00"
                ),
                queue = QueueInfo(
                    patientsAhead = 6,
                    queuePosition = 7
                )
            ),
            onCheckIn = {},
            onViewQueue = {}
        )
    }
}
