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
import androidx.compose.ui.text.font.FontWeight
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

    // Local editable state for interval hours/minutes to avoid saving on every keystroke
    val intervalMinutesTotal = viewModel.countdownIntervalMinutes
    var hoursText by remember(intervalMinutesTotal) { mutableStateOf((intervalMinutesTotal / 60).toString()) }
    var minutesText by remember(intervalMinutesTotal) { mutableStateOf((intervalMinutesTotal % 60).toString()) }

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
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Theme section
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

            // Session section
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

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Countdown section
            Text(
                text = "Countdown",
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

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Day 0 section
            Text(
                text = "Progress",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var showDatePicker by remember { mutableStateOf(false) }
            val day0Set = viewModel.day0Date != null

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Day 0", fontSize = 16.sp)
                    Text(
                        "When it all started",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Checkbox(
                    checked = day0Set,
                    onCheckedChange = { checked ->
                        if (checked) showDatePicker = true
                        else viewModel.updateDay0Date(null)
                    }
                )
            }

            if (day0Set) {
                Spacer(modifier = Modifier.height(12.dp))
                val dateLabel = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    .format(Date(viewModel.day0Date!!))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start date: $dateLabel")
                }
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    // DatePicker works in UTC midnight; convert our stored local midnight back to UTC
                    initialSelectedDateMillis = viewModel.day0Date
                        ?.let { localMidnightToUtcMidnight(it) }
                        ?: System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            // DatePicker returns UTC midnight; store as local midnight so display
                            // and day-count calculations use the correct calendar date
                            datePickerState.selectedDateMillis
                                ?.let { viewModel.updateDay0Date(utcMidnightToLocalMidnight(it)) }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
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
