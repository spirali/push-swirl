package org.kreatrix.pushswirl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SessionViewModel) {
    BackHandler { viewModel.navigateTo(AppScreen.Home) }

    val intervalMinutesTotal = viewModel.countdownIntervalMinutes
    var hoursText by remember(intervalMinutesTotal) { mutableStateOf((intervalMinutesTotal / 60).toString()) }
    var minutesText by remember(intervalMinutesTotal) { mutableStateOf((intervalMinutesTotal % 60).toString()) }
    var selectedTab by remember { mutableStateOf(0) }

    fun commitInterval() {
        val h = hoursText.toIntOrNull()?.coerceIn(0, 99) ?: 0
        val m = minutesText.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val total = h * 60 + m
        if (total > 0) viewModel.updateCountdownIntervalMinutes(total)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Progress") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Countdown") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("App") }
                )
            }

            when (selectedTab) {
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    var showDay0Picker by remember { mutableStateOf(false) }
                    var showMilestonePicker by remember { mutableStateOf(false) }
                    var pendingMilestoneDate by remember { mutableStateOf<Long?>(null) }
                    var pendingComment by remember { mutableStateOf("") }
                    val day0Date = viewModel.day0Date
                    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

                    if (day0Date == null) {
                        Button(
                            onClick = { showDay0Picker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Set Day 0", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Day when it all started",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))) {
                                    append("Day 0: ")
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append(dateFormat.format(Date(day0Date)))
                                }
                            },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showDay0Picker = true }) {
                                Text("Select date")
                            }
                            OutlinedButton(onClick = { viewModel.updateDay0Date(null) }) {
                                Text("Forget date")
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Milestones",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        viewModel.milestones.forEach { milestone ->
                            val dayNumber = ((milestone.date - day0Date) / (1000L * 60 * 60 * 24)).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))) {
                                                append("Day $dayNumber: ")
                                            }
                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                                append(dateFormat.format(Date(milestone.date)))
                                            }
                                        },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (milestone.comment.isNotBlank()) {
                                        Text(
                                            text = milestone.comment,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                TextButton(onClick = { viewModel.removeMilestone(milestone) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showMilestonePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add milestone")
                        }
                    }

                    if (showDay0Picker) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = day0Date
                                ?.let { localMidnightToUtcMidnight(it) }
                                ?: System.currentTimeMillis()
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDay0Picker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis
                                        ?.let { viewModel.updateDay0Date(utcMidnightToLocalMidnight(it)) }
                                    showDay0Picker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDay0Picker = false }) { Text("Cancel") }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    if (showMilestonePicker) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = System.currentTimeMillis()
                        )
                        DatePickerDialog(
                            onDismissRequest = { showMilestonePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let {
                                        pendingMilestoneDate = utcMidnightToLocalMidnight(it)
                                        pendingComment = ""
                                    }
                                    showMilestonePicker = false
                                }) { Text("Next") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showMilestonePicker = false }) { Text("Cancel") }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    if (pendingMilestoneDate != null) {
                        AlertDialog(
                            onDismissRequest = { pendingMilestoneDate = null },
                            title = { Text("Add comment") },
                            text = {
                                OutlinedTextField(
                                    value = pendingComment,
                                    onValueChange = { pendingComment = it },
                                    label = { Text("Comment (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.addMilestone(Milestone(pendingMilestoneDate!!, pendingComment.trim()))
                                    pendingMilestoneDate = null
                                }) { Text("Add") }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingMilestoneDate = null }) { Text("Cancel") }
                            }
                        )
                    }
                }

                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Count down from last session", fontSize = 16.sp)
                            Text(
                                "Show a countdown on the home screen based on your last session",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Checkbox(
                            checked = viewModel.countdownEnabled,
                            onCheckedChange = { viewModel.updateCountdownEnabled(it) }
                        )
                    }

                    if (viewModel.countdownEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Interval", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = hoursText,
                                onValueChange = { hoursText = it.filter { c -> c.isDigit() }.take(2) },
                                label = { Text("Hours") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                trailingIcon = { Text("h", fontSize = 14.sp) }
                            )
                            OutlinedTextField(
                                value = minutesText,
                                onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(2) },
                                label = { Text("Minutes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                trailingIcon = { Text("m", fontSize = 14.sp) }
                            )
                            Button(onClick = { commitInterval() }) {
                                Text("Set")
                            }
                        }
                    }
                }

                2 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Appearance",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text("Theme", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(ThemeMode.AUTO to "Auto", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                            .forEach { (mode, label) ->
                                FilterChip(
                                    selected = viewModel.themeMode == mode,
                                    onClick = { viewModel.updateThemeMode(mode) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Session",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep screen on", fontSize = 16.sp)
                            Text(
                                "Prevent screen from turning off during active session",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Checkbox(
                            checked = viewModel.keepScreenOn,
                            onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                        )
                    }
                }
            }
        }
    }
}

private fun utcMidnightToLocalMidnight(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = utcMillis
    return Calendar.getInstance().apply {
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun localMidnightToUtcMidnight(localMillis: Long): Long {
    val local = Calendar.getInstance()
    local.timeInMillis = localMillis
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
