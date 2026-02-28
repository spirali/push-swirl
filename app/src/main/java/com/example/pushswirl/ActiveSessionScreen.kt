package org.kreatrix.pushswirl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(viewModel: SessionViewModel) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Session", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    TextButton(onClick = { showCancelDialog = true }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = viewModel.sessionState) {
                is SessionState.TTD -> TTDView(viewModel, state.phase)
                is SessionState.DepthInput -> DepthInputView(viewModel, state.phase)
                is SessionState.Dilation -> DilationView(viewModel, state.phase, state.action)
                else -> {}
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Session?") },
            text = { Text("The current session will not be saved. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelSession()
                        showCancelDialog = false
                    }
                ) {
                    Text("Cancel Session", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Continue Session")
                }
            }
        )
    }
}

@Composable
fun TTDView(viewModel: SessionViewModel, phase: PhaseSize) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = phase.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Time to Depth",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = formatTime(viewModel.ttdSeconds),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (viewModel.ttdRunning) {
            Button(
                onClick = { viewModel.pauseTTD() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Pause", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.finishTTD() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("Depth reached", fontSize = 18.sp)
            }
        } else {
            if (viewModel.ttdSeconds > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.startTTD() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Resume", fontSize = 18.sp)
                }
            } else {
                Button(
                    onClick = { viewModel.startTTD() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Start", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun DepthInputView(viewModel: SessionViewModel, phase: PhaseSize) {
    var depthText by remember {
        mutableStateOf(
            if (viewModel.currentDepthInput % 1 == 0f) {
                viewModel.currentDepthInput.toInt().toString()
            } else {
                viewModel.currentDepthInput.toString()
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = phase.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Record Depth",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = depthText,
            onValueChange = { newValue ->
                // Allow digits and single decimal point
                val filtered = newValue.filter { it.isDigit() || it == '.' }

                // Ensure only one decimal point
                val dotCount = filtered.count { it == '.' }
                if (dotCount <= 1) {
                    // Prevent more than one digit after decimal
                    val parts = filtered.split('.')
                    val isValid = when {
                        parts.size == 1 -> true
                        parts.size == 2 -> parts[1].length <= 1
                        else -> false
                    }

                    if (isValid) {
                        depthText = filtered
                        val depth = filtered.toFloatOrNull()
                        if (depth != null && depth in 0.1f..99.9f) {
                            viewModel.updateDepthInput(depth)
                        }
                    }
                }
            },
            label = { Text("Depth (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter the depth reached in centimeters (e.g., 14.5)",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.confirmDepth() },
            enabled = depthText.isNotEmpty() && depthText.toFloatOrNull() != null && depthText.toFloatOrNull()!! in 0.1f..99.9f,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Continue to Dilation", fontSize = 18.sp)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DilationView(viewModel: SessionViewModel, phase: PhaseSize, action: DilationAction) {
    var showEarlyFinishDialog by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = phase.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = action.name,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (action == DilationAction.PUSH)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action countdown (15s)
        Text(
            text = formatTime(viewModel.actionRemainingSeconds.toLong()),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Activity Timer",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Total remaining time
        Text(
            text = formatTime(viewModel.dilationRemainingSeconds.toLong()),
            fontSize = 36.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Total Remaining",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Progress bar
        val progress = 1f - (viewModel.dilationRemainingSeconds.toFloat() / viewModel.dilationTotalSeconds.toFloat())
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Notification controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vibration toggle
            FilterChip(
                selected = viewModel.notificationSettings.vibrationEnabled,
                onClick = {
                    viewModel.updateNotificationSettings(
                        viewModel.notificationSettings.copy(
                            vibrationEnabled = !viewModel.notificationSettings.vibrationEnabled
                        )
                    )
                },
                label = { Text("Vibration") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            // Sound toggle
            FilterChip(
                selected = viewModel.notificationSettings.soundEnabled,
                onClick = {
                    viewModel.updateNotificationSettings(
                        viewModel.notificationSettings.copy(
                            soundEnabled = !viewModel.notificationSettings.soundEnabled
                        )
                    )
                },
                label = { Text("Sound") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pause button
        Button(
            onClick = { viewModel.toggleDilationPause() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewModel.dilationPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                if (viewModel.dilationPaused) "Resume" else "Pause",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Early Finish button
        Button(
            onClick = { showEarlyFinishDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("Early Finish", fontSize = 18.sp)
        }
    }

    // Early Finish confirmation dialog
    if (showEarlyFinishDialog) {
        AlertDialog(
            onDismissRequest = { showEarlyFinishDialog = false },
            title = { Text("Finish Early?") },
            text = {
                Text(
                    "End this dilation phase now with ${formatTime(viewModel.dilationRemainingSeconds.toLong())} remaining? " +
                            "The phase will be recorded with the actual time completed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.earlyFinishDilation()
                        showEarlyFinishDialog = false
                    }
                ) {
                    Text("Finish Early", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEarlyFinishDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }
}

private fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}