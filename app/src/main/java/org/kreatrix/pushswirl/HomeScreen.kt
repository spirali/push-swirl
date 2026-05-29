package org.kreatrix.pushswirl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: SessionViewModel) {
    val context = LocalContext.current

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val lastSessionTimestamp = viewModel.sessions.maxByOrNull { it.timestamp }?.timestamp
    val timeSinceLastSession = lastSessionTimestamp?.let { ts ->
        val totalMinutes = (currentTime - ts) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        if (hours < 24) {
            "${hours}h ${minutes}m"
        } else {
            val days = hours / 24
            val remainingHours = hours % 24
            "$days days $remainingHours hours"
        }
    }

    // Countdown: time remaining until (lastSession + interval), negative means overdue
    data class CountdownInfo(val label: String, val timeStr: String, val overdue: Boolean)
    val countdown: CountdownInfo? = if (viewModel.countdownEnabled && lastSessionTimestamp != null) {
        val targetMs = lastSessionTimestamp + viewModel.countdownIntervalMinutes * 60_000L
        val diffMs = targetMs - currentTime
        val diffMinutes = diffMs / 60_000
        val absMin = Math.abs(diffMinutes)
        val h = absMin / 60
        val m = absMin % 60
        val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
        if (diffMs >= 0) CountdownInfo("Next session in ", timeStr, false)
        else CountdownInfo("", "$timeStr overdue", true)
    } else null

    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    Scaffold { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isWideScreen = maxWidth > 600.dp
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HomeInfoContent(
                            viewModel = viewModel,
                            currentTime = currentTime,
                            timeSinceLastSession = timeSinceLastSession,
                            countdown = countdown?.let { Triple(it.label, it.timeStr, it.overdue) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.navigateTo(AppScreen.NewSession) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Start New Session", fontSize = 18.sp)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HomeNavButtons(viewModel = viewModel, versionName = versionName, showStartButton = false)
                    }
                }
            } else {
                val minHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HomeInfoContent(
                        viewModel = viewModel,
                        currentTime = currentTime,
                        timeSinceLastSession = timeSinceLastSession,
                        countdown = countdown?.let { Triple(it.label, it.timeStr, it.overdue) }
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    HomeNavButtons(viewModel = viewModel, versionName = versionName)
                }
            }
        }
    }
}

@Composable
private fun HomeInfoContent(
    viewModel: SessionViewModel,
    currentTime: Long,
    timeSinceLastSession: String?,
    countdown: Triple<String, String, Boolean>?
) {
    Text(
        text = "Push & Swirl",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    if (viewModel.day0Date != null) {
        val dayNumber = ((currentTime - viewModel.day0Date!!) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Day $dayNumber",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary
        )
    }

    if (timeSinceLastSession != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)) {
                    append("Last session: ")
                }
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )) {
                    append("$timeSinceLastSession ago")
                }
            }
        )
    }

    if (countdown != null) {
        val (label, timeStr, overdue) = countdown
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                if (label.isNotEmpty()) {
                    withStyle(SpanStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 15.sp
                    )) { append(label) }
                }
                withStyle(SpanStyle(
                    color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )) { append(timeStr) }
            }
        )
    }

    val pendingResume = viewModel.pendingResume
    if (pendingResume != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Interrupted session found",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (pendingResume.completedPhases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${pendingResume.completedPhases.size} phase(s) completed",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.resumeSession() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Resume") }
                    OutlinedButton(
                        onClick = { viewModel.discardInterruptedSession() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Discard") }
                }
            }
        }
    }
}

@Composable
private fun HomeNavButtons(viewModel: SessionViewModel, versionName: String?, showStartButton: Boolean = true) {
    if (showStartButton) {
        Button(
            onClick = { viewModel.navigateTo(AppScreen.NewSession) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Start New Session", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    OutlinedButton(
        onClick = { viewModel.navigateTo(AppScreen.SessionHistory) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors()
    ) {
        Text("Session History", fontSize = 18.sp)
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = { viewModel.navigateTo(AppScreen.Statistics) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text("Statistics", fontSize = 18.sp)
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = { viewModel.navigateTo(AppScreen.Settings) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text("Settings", fontSize = 18.sp)
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = { viewModel.navigateTo(AppScreen.ImportExport) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text("Import / Export", fontSize = 18.sp)
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "v$versionName",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    )
}
