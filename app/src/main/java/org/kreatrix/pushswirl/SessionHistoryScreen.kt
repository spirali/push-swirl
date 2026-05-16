package org.kreatrix.pushswirl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(viewModel: SessionViewModel) {
    BackHandler { viewModel.navigateTo(AppScreen.Home) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    TextButton(onClick = { viewModel.navigateTo(AppScreen.Home) }) {
                        Text("Back", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
            )
        }
    ) { padding ->
        if (viewModel.sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No sessions yet",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                SortControlsRow(
                    sortField = viewModel.historySortField,
                    ascending = viewModel.historySortAscending,
                    onFieldChange = { viewModel.historySortField = it },
                    onToggleDirection = { viewModel.historySortAscending = !viewModel.historySortAscending }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.sortedSessions) { session ->
                        SessionCard(
                            session = session,
                            onDelete = { viewModel.deleteSession(session.id) },
                            onUpdate = { viewModel.updateSession(it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortControlsRow(
    sortField: HistorySortField,
    ascending: Boolean,
    onFieldChange: (HistorySortField) -> Unit,
    onToggleDirection: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = sortField.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Sort by") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                singleLine = true
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                HistorySortField.entries.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field.label) },
                        onClick = { onFieldChange(field); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        IconButton(onClick = onToggleDirection) {
            Icon(
                imageVector = if (ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (ascending) "Ascending" else "Descending"
            )
        }
    }
}

private data class PhaseEditState(
    val ttdMinutes: String,
    val ttdSeconds: String,
    val depthCm: String
)

@Composable
fun SessionCard(session: Session, onDelete: () -> Unit, onUpdate: (Session) -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editPhases by remember { mutableStateOf<List<PhaseEditState>>(emptyList()) }

    fun startEdit() {
        editPhases = session.phases.map { phase ->
            PhaseEditState(
                ttdMinutes = (phase.ttdSeconds / 60).toString(),
                ttdSeconds = (phase.ttdSeconds % 60).toString(),
                depthCm = phase.depthCm?.let {
                    if (it % 1 == 0f) it.toInt().toString() else String.format("%.1f", it)
                } ?: ""
            )
        }
        isEditing = true
    }

    fun saveEdit() {
        val updatedPhases = session.phases.mapIndexed { i, phase ->
            val mins = editPhases[i].ttdMinutes.toLongOrNull() ?: 0L
            val secs = editPhases[i].ttdSeconds.toLongOrNull() ?: 0L
            val newDepth = if (phase.depthCm != null) editPhases[i].depthCm.toFloatOrNull() else null
            phase.copy(ttdSeconds = mins * 60 + secs, depthCm = newDepth)
        }
        onUpdate(session.copy(phases = updatedPhases))
        isEditing = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDate(session.timestamp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total: ${formatDuration(session.totalSeconds)}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }

                Row {
                    if (isEditing) {
                        TextButton(onClick = { saveEdit() }) { Text("Save") }
                        TextButton(onClick = { isEditing = false }) { Text("Cancel") }
                    } else {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Hide" else "Details")
                        }
                    }
                }
            }

            if (expanded || isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(4.dp))

                if (!isEditing) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)) {
                        TextButton(
                            onClick = { startEdit() },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) { Text("Edit", fontSize = 15.sp) }
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) { Text("Delete", fontSize = 15.sp, color = MaterialTheme.colorScheme.error) }
                    }
                }

                session.phases.forEachIndexed { i, phase ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = phase.size.name, fontWeight = FontWeight.Medium)
                                if (phase.wasFinishedEarly) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "Early",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (isEditing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("TTD:", fontSize = 13.sp)
                                    OutlinedTextField(
                                        value = editPhases[i].ttdMinutes,
                                        onValueChange = {
                                            editPhases = editPhases.toMutableList().also { list ->
                                                list[i] = list[i].copy(ttdMinutes = it.filter(Char::isDigit))
                                            }
                                        },
                                        label = { Text("min") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(64.dp)
                                    )
                                    OutlinedTextField(
                                        value = editPhases[i].ttdSeconds,
                                        onValueChange = {
                                            editPhases = editPhases.toMutableList().also { list ->
                                                list[i] = list[i].copy(ttdSeconds = it.filter(Char::isDigit))
                                            }
                                        },
                                        label = { Text("sec") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(64.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "TTD: ${formatDuration(phase.ttdSeconds)}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (!isEditing) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (phase.wasFinishedEarly) {
                                    Text(
                                        text = "Dilation: ${formatDurationSeconds(phase.actualDilationSeconds)} / ${phase.dilationMinutes}m planned",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else {
                                    Text(
                                        text = "Dilation: ${phase.dilationMinutes}m",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                                if (phase.actionTime != null) {
                                    Text(
                                        text = "Actions: ${phase.actionTime}s",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        if (phase.depthCm != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editPhases[i].depthCm,
                                    onValueChange = {
                                        editPhases = editPhases.toMutableList().also { list ->
                                            list[i] = list[i].copy(depthCm = it)
                                        }
                                    },
                                    label = { Text("Depth (cm)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(140.dp)
                                )
                            } else {
                                val depthFormatted = if (phase.depthCm % 1 == 0f) {
                                    "${phase.depthCm.toInt()} cm"
                                } else {
                                    String.format("%.1f cm", phase.depthCm)
                                }
                                Text(
                                    text = "Depth: $depthFormatted",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
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
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

private fun formatDurationSeconds(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}
