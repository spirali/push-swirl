package org.kreatrix.pushswirl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class PhaseEditState(
    val ttdMinutes: String,
    val ttdSeconds: String,
    val depthCm: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(viewModel: SessionViewModel) {
    val session = (viewModel.currentScreen as AppScreen.EditSession).session
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    fun cancel() = viewModel.navigateTo(AppScreen.SessionHistory)

    BackHandler { cancel() }

    var editPhases by remember {
        mutableStateOf(session.phases.map { phase ->
            PhaseEditState(
                ttdMinutes = (phase.ttdSeconds / 60).toString(),
                ttdSeconds  = (phase.ttdSeconds % 60).toString(),
                depthCm    = phase.depthCm?.let {
                    if (it % 1 == 0f) it.toInt().toString() else String.format(Locale.ROOT, "%.1f", it)
                } ?: ""
            )
        })
    }
    var editTotalMinutes by remember { mutableStateOf((session.totalSeconds / 60).toString()) }
    var editTotalSeconds by remember { mutableStateOf((session.totalSeconds % 60).toString()) }
    var editTagIds by remember { mutableStateOf(session.tagIds) }
    var editNote   by remember { mutableStateOf(session.note) }

    var editStartTimestamp by remember {
        mutableStateOf(session.startTimestamp ?: (session.timestamp - session.totalSeconds * 1000))
    }
    var editEndTimestamp by remember { mutableStateOf(session.timestamp) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    fun save() {
        val updatedPhases = session.phases.mapIndexed { i, phase ->
            val mins     = editPhases[i].ttdMinutes.toLongOrNull() ?: 0L
            val secs     = editPhases[i].ttdSeconds.toLongOrNull() ?: 0L
            val newDepth = if (phase.depthCm != null) editPhases[i].depthCm.replace(',', '.').toFloatOrNull() else null
            phase.copy(ttdSeconds = mins * 60 + secs, depthCm = newDepth)
        }
        val totalMins = editTotalMinutes.toLongOrNull() ?: 0L
        val totalSecs = editTotalSeconds.toLongOrNull() ?: 0L
        viewModel.updateSession(
            session.copy(
                phases = updatedPhases,
                totalSeconds = totalMins * 60 + totalSecs,
                tagIds = editTagIds,
                note = editNote.trim(),
                startTimestamp = editStartTimestamp,
                timestamp = editEndTimestamp
            )
        )
        viewModel.navigateTo(AppScreen.SessionHistory)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Session", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    TextButton(onClick = { cancel() }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) {
                        Text("Save", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Start",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showStartDatePicker = true }) {
                    Text(dateFormatter.format(Date(editStartTimestamp)))
                }
                OutlinedButton(onClick = { showStartTimePicker = true }) {
                    Text(timeFormatter.format(Date(editStartTimestamp)))
                }
            }

            Text(
                text = "End",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showEndDatePicker = true }) {
                    Text(dateFormatter.format(Date(editEndTimestamp)))
                }
                OutlinedButton(onClick = { showEndTimePicker = true }) {
                    Text(timeFormatter.format(Date(editEndTimestamp)))
                }
            }

            HorizontalDivider()

            session.phases.forEachIndexed { i, phase ->
                Text(
                    text = phase.size.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("TTD:", fontSize = 14.sp)
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
                        modifier = Modifier.width(80.dp)
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
                        modifier = Modifier.width(80.dp)
                    )
                }
                if (phase.depthCm != null) {
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
                        modifier = Modifier.width(160.dp)
                    )
                }
                if (i < session.phases.lastIndex) {
                    HorizontalDivider()
                }
            }

            HorizontalDivider()

            Text(
                text = "Total Length",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editTotalMinutes,
                    onValueChange = { editTotalMinutes = it.filter(Char::isDigit) },
                    label = { Text("min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
                OutlinedTextField(
                    value = editTotalSeconds,
                    onValueChange = { editTotalSeconds = it.filter(Char::isDigit) },
                    label = { Text("sec") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
            }

            HorizontalDivider()

            if (viewModel.tags.isNotEmpty()) {
                Text(
                    text = "Tags",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    viewModel.tags.forEach { tag ->
                        val isSelected = tag.id in editTagIds
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                editTagIds = if (isSelected) editTagIds - tag.id
                                             else editTagIds + tag.id
                            },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(tag.color.toComposeColor())
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
                HorizontalDivider()
            }

            Text(
                text = "Note",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = editNote,
                onValueChange = { editNote = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localMidnightToUtcMidnight(editStartTimestamp)
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        editStartTimestamp = withDate(editStartTimestamp, it)
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localMidnightToUtcMidnight(editEndTimestamp)
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        editEndTimestamp = withDate(editEndTimestamp, it)
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTimestamp = editStartTimestamp,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                editStartTimestamp = withTime(editStartTimestamp, hour, minute)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTimestamp = editEndTimestamp,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                editEndTimestamp = withTime(editEndTimestamp, hour, minute)
                showEndTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTimestamp: Long,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val initialCalendar = Calendar.getInstance().apply { timeInMillis = initialTimestamp }
    val timePickerState = rememberTimePickerState(
        initialHour = initialCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCalendar.get(Calendar.MINUTE),
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

private fun withDate(original: Long, utcMidnightMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMillis }
    return Calendar.getInstance().apply {
        timeInMillis = original
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun withTime(original: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = original
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun localMidnightToUtcMidnight(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
