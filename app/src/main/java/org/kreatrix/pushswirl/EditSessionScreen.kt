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
import java.util.Locale

private data class PhaseEditState(
    val ttdMinutes: String,
    val ttdSeconds: String,
    val depthCm: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditSessionScreen(viewModel: SessionViewModel) {
    val session = (viewModel.currentScreen as AppScreen.EditSession).session

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
                note = editNote.trim()
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
                    Divider()
                }
            }

            Divider()

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

            Divider()

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
                Divider()
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
}
