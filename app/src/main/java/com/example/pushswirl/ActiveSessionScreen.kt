package com.example.pushswirl

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                is SessionState.Dilation -> DilationView(viewModel, state.phase, state.part)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { viewModel.pauseTTD() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Pause", fontSize = 18.sp)
                }

                Button(
                    onClick = { viewModel.finishTTD() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Depth reached", fontSize = 18.sp)
                }
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
@OptIn(ExperimentalMaterial3Api::class)
fun DilationView(viewModel: SessionViewModel, phase: PhaseSize, part: DilationPart) {
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
            text = part.name,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (part == DilationPart.PUSH)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Part countdown (15s)
        Text(
            text = formatTime(viewModel.partRemainingSeconds.toLong()),
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
    }
}

private fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}