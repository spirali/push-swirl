package org.kreatrix.pushswirl

import androidx.compose.foundation.layout.*
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val versionName = remember {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }

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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    buildAnnotatedString {
                        if (countdown.label.isNotEmpty()) {
                            withStyle(SpanStyle(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                fontSize = 15.sp
                            )) { append(countdown.label) }
                        }
                        withStyle(SpanStyle(
                            color = if (countdown.overdue) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )) { append(countdown.timeStr) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "v$versionName",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
