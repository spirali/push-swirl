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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsFilterScreen(viewModel: SessionViewModel) {
    val milestones = viewModel.milestones
    val day0Date = viewModel.day0Date

    var daysText by remember { mutableStateOf(viewModel.statsFilterDays?.toString() ?: "") }
    var excluded by remember { mutableStateOf(viewModel.statsExcludedPeriodKeys) }

    fun applyAndBack() {
        viewModel.updateStatsFilter(daysText.toIntOrNull(), excluded)
        viewModel.navigateTo(AppScreen.Statistics)
    }

    BackHandler { applyAndBack() }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val sortedMilestones = remember(milestones) { milestones.sortedBy { it.date } }
    val periodKeys: List<Long?> = remember(sortedMilestones) {
        buildList {
            if (sortedMilestones.isNotEmpty()) add(null)
            sortedMilestones.forEach { add(it.date) }
        }
    }

    val milestoneNameColor = MaterialTheme.colorScheme.secondary

    fun makeName(label: String, comment: String?): AnnotatedString = buildAnnotatedString {
        append(label)
        if (!comment.isNullOrBlank()) {
            append(" (")
            withStyle(SpanStyle(color = milestoneNameColor)) { append(comment) }
            append(")")
        }
    }

    fun periodLabel(key: Long?): AnnotatedString {
        if (sortedMilestones.isEmpty()) return buildAnnotatedString { append("All") }
        return buildAnnotatedString {
            if (key == null) {
                val endMilestone = sortedMilestones.first()
                if (day0Date != null) {
                    val endDay = ((endMilestone.date - day0Date) / 86_400_000).toInt()
                    append(makeName("D0", null))
                    append(" - ")
                    append(makeName("D$endDay", endMilestone.comment.takeIf { it.isNotBlank() }))
                } else {
                    append("– ")
                    append(makeName(dateFormat.format(Date(endMilestone.date)), endMilestone.comment.takeIf { it.isNotBlank() }))
                }
            } else {
                val idx = sortedMilestones.indexOfFirst { it.date == key }
                val startMilestone = sortedMilestones.getOrNull(idx)
                val endMilestone = sortedMilestones.getOrNull(idx + 1)
                if (day0Date != null) {
                    val startDay = ((key - day0Date) / 86_400_000).toInt()
                    append(makeName("D$startDay", startMilestone?.comment?.takeIf { it.isNotBlank() }))
                    if (endMilestone != null) {
                        val endDay = ((endMilestone.date - day0Date) / 86_400_000).toInt()
                        append(" - ")
                        append(makeName("D$endDay", endMilestone.comment.takeIf { it.isNotBlank() }))
                    } else {
                        append("+")
                    }
                } else {
                    append(makeName(dateFormat.format(Date(key)), startMilestone?.comment?.takeIf { it.isNotBlank() }))
                    if (endMilestone != null) {
                        append(" - ")
                        append(makeName(dateFormat.format(Date(endMilestone.date)), endMilestone.comment.takeIf { it.isNotBlank() }))
                    } else {
                        append("+")
                    }
                }
            }
        }
    }

    val quickOptions = listOf(7, 14, 30, 90)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filter", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    TextButton(onClick = { applyAndBack() }) {
                        Text("Back", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = { applyAndBack() }) {
                        Text("Apply", color = MaterialTheme.colorScheme.onPrimary)
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Time range", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)

            FilterChip(
                selected = daysText.isEmpty(),
                onClick = { daysText = "" },
                label = { Text("All time") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    labelColor = MaterialTheme.colorScheme.tertiary,
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickOptions.forEach { d ->
                    FilterChip(
                        selected = daysText == d.toString(),
                        onClick = { daysText = d.toString() },
                        label = { Text("${d}d") }
                    )
                }
            }

            if (daysText.isNotEmpty()) {
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter(Char::isDigit) },
                    label = { Text("Custom days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (periodKeys.isNotEmpty()) {
                Divider()
                Text("Periods", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)

                periodKeys.forEach { key ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = key !in excluded,
                            onCheckedChange = { checked ->
                                excluded = if (checked) excluded - key else excluded + key
                            }
                        )
                        Text(
                            text = periodLabel(key),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
