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

    fun periodLabel(key: Long?): String {
        val range: String
        val startComment: String?
        val endComment: String?

        if (key == null) {
            val endMilestone = sortedMilestones.firstOrNull() ?: return "All"
            range = if (day0Date != null) "D0-D${((endMilestone.date - day0Date) / 86_400_000).toInt()}"
                    else "– ${dateFormat.format(Date(endMilestone.date))}"
            startComment = null
            endComment = endMilestone.comment.takeIf { it.isNotBlank() }
        } else {
            val idx = sortedMilestones.indexOfFirst { it.date == key }
            val startMilestone = sortedMilestones.getOrNull(idx)
            val endMilestone = sortedMilestones.getOrNull(idx + 1)
            range = if (day0Date != null) {
                val startDay = ((key - day0Date) / 86_400_000).toInt()
                if (endMilestone != null) "D$startDay-D${((endMilestone.date - day0Date) / 86_400_000).toInt()}"
                else "D$startDay+"
            } else {
                val start = dateFormat.format(Date(key))
                if (endMilestone != null) "$start-${dateFormat.format(Date(endMilestone.date))}" else "$start+"
            }
            startComment = startMilestone?.comment?.takeIf { it.isNotBlank() }
            endComment = endMilestone?.comment?.takeIf { it.isNotBlank() }
        }

        val commentPart = listOfNotNull(startComment, endComment).joinToString(" - ")
        return if (commentPart.isNotEmpty()) "$range: $commentPart" else range
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
