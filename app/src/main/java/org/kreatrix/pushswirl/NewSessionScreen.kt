package org.kreatrix.pushswirl

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

    var small by remember { mutableStateOf(viewModel.sessionConfig.small) }
    var medium by remember { mutableStateOf(viewModel.sessionConfig.medium) }
    var large by remember { mutableStateOf(viewModel.sessionConfig.large) }
    var xl by remember { mutableStateOf(viewModel.sessionConfig.xl) }
    var actionTime by remember { mutableStateOf(viewModel.sessionConfig.actionTime) }
    var recordDepth by remember { mutableStateOf(viewModel.sessionConfig.recordDepth) }
    var selectedTab by remember { mutableStateOf(0) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Button(
                onClick = {
                    val config = SessionConfig(small, medium, large, xl, actionTime, recordDepth)
                    viewModel.updateConfig(config)
                    viewModel.startSession()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start Session", fontSize = 18.sp)
            }

            TabRow(selectedTabIndex = selectedTab) {
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
                    PhaseSelector("Small", small) { small = it }
                    Spacer(modifier = Modifier.height(12.dp))
                    PhaseSelector("Medium", medium) { medium = it }
                    Spacer(modifier = Modifier.height(12.dp))
                    PhaseSelector("Large", large) { large = it }
                    Spacer(modifier = Modifier.height(12.dp))
                    PhaseSelector("XL", xl) { xl = it }
                }
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    ActionTimeSelector(actionTime) { actionTime = it }

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
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun PhaseSelector(
    label: String,
    selected: PhaseDuration,
    onSelect: (PhaseDuration) -> Unit
) {
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
            PhaseDuration.entries.forEach { duration ->
                val isSelected = selected == duration
                val chipLabel = when (duration) {
                    PhaseDuration.SKIP -> "X"
                    else -> "${duration.minutes}m"
                }

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
