package org.kreatrix.pushswirl

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: SessionViewModel) {
    val context = LocalContext.current
    var showNoDataDialog by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var importResultMessage by remember { mutableStateOf<String?>(null) }
    var exportResultMessage by remember { mutableStateOf<String?>(null) }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val lastSessionTimestamp = viewModel.sessions.maxByOrNull { it.timestamp }?.timestamp
    val timeSinceLastSession = lastSessionTimestamp?.let { ts ->
        val totalMinutes = (currentTime - ts) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        if (hours < 24) {
            "${hours}h ${minutes}m"
        } else {
            val days = hours / 24
            val remainingHours = hours % 24
            "$days days $remainingHours hours"
        }
    }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            when (val result = viewModel.importSessions(it)) {
                is ImportResult.Success -> {
                    importResultMessage = if (result.skipped > 0) {
                        "Imported ${result.imported} session(s)\n${result.skipped} duplicate(s) skipped"
                    } else {
                        "Successfully imported ${result.imported} session(s)"
                    }
                }
                is ImportResult.Error -> {
                    importResultMessage = "Import failed: ${result.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PushSwirl", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
            Text(
                text = "Push & Swirl",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (timeSinceLastSession != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)) {
                            append("Last session: ")
                        }
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )) {
                            append("$timeSinceLastSession ago")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { viewModel.navigateTo(AppScreen.NewSession) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start New Session", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.navigateTo(AppScreen.SessionHistory) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Session History", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.navigateTo(AppScreen.Statistics) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Statistics", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    if (viewModel.sessions.isNotEmpty()) {
                        showExportOptionsDialog = true
                    } else {
                        showNoDataDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Export Data", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    importLauncher.launch("application/json")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Import Data", fontSize = 18.sp)
            }
        }
    }

    // No data dialog
    if (showNoDataDialog) {
        AlertDialog(
            onDismissRequest = { showNoDataDialog = false },
            title = { Text("No Data") },
            text = { Text("There are no sessions to export yet.") },
            confirmButton = {
                TextButton(onClick = { showNoDataDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Export options dialog
    if (showExportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showExportOptionsDialog = false },
            title = { Text("Export Data") },
            text = { Text("Choose how to export your sessions:") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportOptionsDialog = false
                        when (val result = viewModel.saveExportToDownloads()) {
                            is ExportResult.Success -> {
                                exportResultMessage = "Saved to Downloads folder:\n${result.filename}"
                            }
                            is ExportResult.Error -> {
                                exportResultMessage = result.message
                            }
                        }
                    }
                ) {
                    Text("Save as file")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportOptionsDialog = false
                        val uri = viewModel.exportSessions()
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Export File"))
                        }
                    }
                ) {
                    Text("Share")
                }
            }
        )
    }

    // Import result dialog
    if (importResultMessage != null) {
        AlertDialog(
            onDismissRequest = { importResultMessage = null },
            title = { Text("Import Result") },
            text = { Text(importResultMessage!!) },
            confirmButton = {
                TextButton(onClick = { importResultMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Export result dialog
    if (exportResultMessage != null) {
        AlertDialog(
            onDismissRequest = { exportResultMessage = null },
            title = { Text("Export Result") },
            text = { Text(exportResultMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportResultMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}