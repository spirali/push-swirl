package org.kreatrix.pushswirl

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(viewModel: SessionViewModel) {
    BackHandler { viewModel.navigateTo(AppScreen.Home) }

    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var importResultMessage by remember { mutableStateOf<String?>(null) }
    var exportResultMessage by remember { mutableStateOf<String?>(null) }

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
                is ImportResult.Error -> importResultMessage = "Import failed: ${result.message}"
            }
        }
    }

    val butterflyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            when (val result = viewModel.importFromButterfly(it)) {
                is ImportResult.Success -> {
                    importResultMessage = if (result.skipped > 0) {
                        "Imported ${result.imported} session(s) from Butterfly\n${result.skipped} duplicate(s) skipped"
                    } else {
                        "Successfully imported ${result.imported} session(s) from Butterfly"
                    }
                }
                is ImportResult.Error -> importResultMessage = "Butterfly import failed: ${result.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import / Export", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    TextButton(onClick = { viewModel.navigateTo(AppScreen.Home) }) {
                        Text("Back", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isWideScreen = maxWidth > 600.dp
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "PushSwirl",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Button(
                            onClick = { importLauncher.launch("application/json") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import")
                        }
                        Button(
                            onClick = { showExportOptionsDialog = true },
                            enabled = viewModel.sessions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Export")
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Butterfly App",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Button(
                            onClick = { butterflyLauncher.launch("text/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Import from Butterfly CSV")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Data",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Button(
                            onClick = { showDeleteDialog = true },
                            enabled = viewModel.sessions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Clear All Sessions")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PushSwirl",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick = { importLauncher.launch("application/json") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import")
                    }
                    Button(
                        onClick = { showExportOptionsDialog = true },
                        enabled = viewModel.sessions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Butterfly App",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick = { butterflyLauncher.launch("text/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Import from Butterfly CSV")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Data",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick = { showDeleteDialog = true },
                        enabled = viewModel.sessions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear All Sessions")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Sessions?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllSessions()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showExportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showExportOptionsDialog = false },
            title = { Text("Export Data") },
            text = { Text("Choose how to export your sessions:") },
            confirmButton = {
                TextButton(onClick = {
                    showExportOptionsDialog = false
                    when (val result = viewModel.saveExportToDownloads()) {
                        is ExportResult.Success -> exportResultMessage = "Saved to Downloads folder:\n${result.filename}"
                        is ExportResult.Error -> exportResultMessage = result.message
                    }
                }) {
                    Text("Save as file")
                }
            },
            dismissButton = {
                TextButton(onClick = {
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
                }) {
                    Text("Share")
                }
            }
        )
    }

    if (importResultMessage != null) {
        AlertDialog(
            onDismissRequest = { importResultMessage = null },
            title = { Text("Import Result") },
            text = { Text(importResultMessage!!) },
            confirmButton = {
                TextButton(onClick = { importResultMessage = null }) { Text("OK") }
            }
        )
    }

    if (exportResultMessage != null) {
        AlertDialog(
            onDismissRequest = { exportResultMessage = null },
            title = { Text("Export Result") },
            text = { Text(exportResultMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportResultMessage = null }) { Text("OK") }
            }
        )
    }
}
