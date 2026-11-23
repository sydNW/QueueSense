
package com.project.queuesense.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project.queuesense.data.model.TicketResponse

@Composable
fun ProfileScreen(
    ticket: TicketResponse?,
    onOpenAccuracy: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "My Profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Your personal and queue information.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Patient Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                ProfileDetail(
                    label = "Full Name",
                    value = ticket?.ticket?.fullName ?: "No patient information"
                )

                ProfileDetail(
                    label = "Ticket Code",
                    value = ticket?.ticket?.ticketCode ?: "No active ticket"
                )

                ProfileDetail(
                    label = "Department",
                    value = ticket?.ticket?.department ?: "Not available"
                )

                ProfileDetail(
                    label = "Check-in Time",
                    value = ticket?.ticket?.checkedInAt ?: "Not available"
                )

                ProfileDetail(
                    label = "Ticket Status",
                    value = ticket?.ticket?.status?.replaceFirstChar {
                        it.uppercase()
                    } ?: "No active ticket"
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onOpenAccuracy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Prediction Accuracy")
        }

        OutlinedButton(
            onClick = onLogOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out")
        }
    }
}

@Composable
private fun ProfileDetail(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen(
        ticket = null,
        onOpenAccuracy = {},
        onLogOut = {}
    )
}
