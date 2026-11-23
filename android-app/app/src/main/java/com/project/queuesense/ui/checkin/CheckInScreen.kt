
package com.project.queuesense.ui.checkin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project.queuesense.data.SampleData
import com.project.queuesense.ui.theme.QueueSenseTheme

@Composable
fun CheckInScreen(
    departments: List<String>,
    isSubmitting: Boolean,
    errorMessage: String?,
    accountName: String,
    accountMobile: String,
    onJoinQueue: (fullName: String, mobile: String, department: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var department by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    val canJoin = department.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "You're ready to check in!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Welcome, $accountName. Your account details are already saved.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Select department",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = department,
                onValueChange = {},
                readOnly = true,
                label = { Text("Department") },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { menuOpen = true }
            )

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                departments.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            department = name
                            menuOpen = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Checking in as",
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = accountName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = accountMobile,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                onJoinQueue(accountName, accountMobile, department)
            },
            enabled = canJoin && !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(if (isSubmitting) "Joining..." else "Join Queue")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckInScreenPreview() {
    QueueSenseTheme {
        CheckInScreen(
            departments = SampleData.departments,
            isSubmitting = false,
            errorMessage = null,
            accountName = "Sydney",
            accountMobile = "0712345678",
            onJoinQueue = { _, _, _ -> }
        )
    }
}
