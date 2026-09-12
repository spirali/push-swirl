package org.kreatrix.pushswirl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(viewModel: SessionViewModel) {
    BackHandler { viewModel.navigateTo(AppScreen.Home) }

    val custom = viewModel.customPhaseTimes
    var small by remember { mutableStateOf(resolvePhaseMinutes(viewModel.sessionConfig.small, custom.small)) }
    var medium by remember { mutableStateOf(resolvePhaseMinutes(viewModel.sessionConfig.medium, custom.medium)) }
    var large by remember { mutableStateOf(resolvePhaseMinutes(viewModel.sessionConfig.large, custom.large)) }
    var xl by remember { mutableStateOf(resolvePhaseMinutes(viewModel.sessionConfig.xl, custom.xl)) }
    var actionTime by remember { mutableStateOf(viewModel.sessionConfig.actionTime) }
    var pauseSeconds by remember { mutableStateOf(viewModel.sessionConfig.pauseSeconds) }
    var recordDepth by remember { mutableStateOf(viewModel.sessionConfig.recordDepth) }
    var addTagsNoteAtEnd by remember { mutableStateOf(viewModel.sessionConfig.addTagsNoteAtEnd) }
    var blindedTtdTimer by remember { mutableStateOf(viewModel.sessionConfig.blindedTtdTimer) }
    var selectedTab by remember { mutableStateOf(0) }

    // A phase sitting on the custom slot follows the custom time when it is edited in Settings.
    LaunchedEffect(custom) {
        small = resolvePhaseMinutes(small, custom.small)
        medium = resolvePhaseMinutes(medium, custom.medium)
        large = resolvePhaseMinutes(large, custom.large)
        xl = resolvePhaseMinutes(xl, custom.xl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Session", fontWeight = FontWeight.Bold) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isWideScreen = LocalIsWideScreen.current

            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left: vertical tab rail + Start button
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                    ) {
                        NavigationDrawerItem(
                            label = { Text("Phases") },
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        NavigationDrawerItem(
                            label = { Text("Others") },
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                viewModel.updateConfig(SessionConfig(small, medium, large, xl, actionTime, recordDepth, addTagsNoteAtEnd, blindedTtdTimer, pauseSeconds))
                                viewModel.startSession()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Start", fontSize = 16.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    // Right: tab content
                    when (selectedTab) {
                        0 -> Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    PhaseSelector("Small", custom.small, small) { small = it }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    PhaseSelector("Large", custom.large, large) { large = it }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    PhaseSelector("Medium", custom.medium, medium) { medium = it }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    PhaseSelector("XL", custom.xl, xl) { xl = it }
                                }
                            }
                        }
                        1 -> Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            ActionTimeSelector(actionTime) { actionTime = it }
                            Spacer(modifier = Modifier.height(24.dp))
                            PauseSelector(pauseSeconds) { pauseSeconds = it }
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Record Reached Depth",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Checkbox(
                                    checked = recordDepth,
                                    onCheckedChange = { recordDepth = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Add tags/note at end",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Checkbox(
                                    checked = addTagsNoteAtEnd,
                                    onCheckedChange = { addTagsNoteAtEnd = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Blinded TTD timer",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Checkbox(
                                    checked = blindedTtdTimer,
                                    onCheckedChange = { blindedTtdTimer = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = {
                            viewModel.updateConfig(SessionConfig(small, medium, large, xl, actionTime, recordDepth, addTagsNoteAtEnd, blindedTtdTimer, pauseSeconds))
                            viewModel.startSession()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = 400.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Start Session", fontSize = 18.sp)
                    }
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Phases") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Others") }
                        )
                    }
                    when (selectedTab) {
                        0 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.Start
                        ) {
                            PhaseSelector("Small", custom.small, small) { small = it }
                            Spacer(modifier = Modifier.height(12.dp))
                            PhaseSelector("Medium", custom.medium, medium) { medium = it }
                            Spacer(modifier = Modifier.height(12.dp))
                            PhaseSelector("Large", custom.large, large) { large = it }
                            Spacer(modifier = Modifier.height(12.dp))
                            PhaseSelector("XL", custom.xl, xl) { xl = it }
                        }
                        1 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.Start
                        ) {
                            ActionTimeSelector(actionTime) { actionTime = it }
                            Spacer(modifier = Modifier.height(24.dp))
                            PauseSelector(pauseSeconds) { pauseSeconds = it }
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Record Reached Depth",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Checkbox(
                                    checked = recordDepth,
                                    onCheckedChange = { recordDepth = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Add tags/note at end",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Checkbox(
                                    checked = addTagsNoteAtEnd,
                                    onCheckedChange = { addTagsNoteAtEnd = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Blinded TTD timer",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Checkbox(
                                    checked = blindedTtdTimer,
                                    onCheckedChange = { blindedTtdTimer = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Durations every phase offers; anything else means "whatever the custom time currently is". */
private val FIXED_PHASE_OPTIONS = listOf(PHASE_SKIPPED, 5, 10, 15)

/**
 * Resolves a stored duration against the current custom time for its phase: a fixed option is
 * kept as-is, anything else is treated as the custom slot and follows the custom time. Keeps
 * a custom selection selected (rather than adding a chip) when Settings -> Custom times changes.
 */
private fun resolvePhaseMinutes(stored: Int, customMinutes: Int): Int =
    if (stored in FIXED_PHASE_OPTIONS) stored else customMinutes

/**
 * Duration chips for one phase: skip, the fixed 5/10/15 options and the user's custom time
 * for this phase (Settings -> Custom times). The custom chip is outlined to set it apart.
 */
@Composable
fun PhaseSelector(
    label: String,
    customMinutes: Int,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val options = remember(customMinutes) {
        (FIXED_PHASE_OPTIONS + customMinutes).distinct()
    }
    Column {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { minutes ->
                val chipLabel = if (minutes == PHASE_SKIPPED) "X" else "${minutes}m"
                val isSelected = selected == minutes
                val isCustom = minutes == customMinutes

                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(minutes) },
                    label = { Text(chipLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = if (isCustom) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                    } else {
                        FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected)
                    }
                )
            }
        }
    }
}

@Composable
fun ActionTimeSelector(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            text = "Action Time",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 10, 15, 20, 30).forEach { duration ->
                val isSelected = selected == duration
                val chipLabel = if (duration == 0) "Static" else "${duration}s"
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(duration) },
                    label = { Text(chipLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun PauseSelector(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            text = "Breaks",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 2, 5).forEach { seconds ->
                val isSelected = selected == seconds
                val chipLabel = if (seconds == 0) "None" else "${seconds}s"
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(seconds) },
                    label = { Text(chipLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}
