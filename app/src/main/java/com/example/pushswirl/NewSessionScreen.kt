package com.example.pushswirl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(viewModel: SessionViewModel) {
    var small by remember { mutableStateOf(viewModel.sessionConfig.small) }
    var medium by remember { mutableStateOf(viewModel.sessionConfig.medium) }
    var large by remember { mutableStateOf(viewModel.sessionConfig.large) }
    var xl by remember { mutableStateOf(viewModel.sessionConfig.xl) }

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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Phases",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            PhaseSelector("Small", small) { small = it }
            Spacer(modifier = Modifier.height(12.dp))
            PhaseSelector("Medium", medium) { medium = it }
            Spacer(modifier = Modifier.height(12.dp))
            PhaseSelector("Large", large) { large = it }
            Spacer(modifier = Modifier.height(12.dp))
            PhaseSelector("XL", xl) { xl = it }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    val config = SessionConfig(small, medium, large, xl)
                    viewModel.updateConfig(config)
                    viewModel.startSession()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start Session", fontSize = 18.sp)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhaseDuration.entries.forEach { duration ->
                val isSelected = selected == duration
                val label = when (duration) {
                    PhaseDuration.SKIP -> "X"
                    else -> "${duration.minutes}"
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(duration) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}