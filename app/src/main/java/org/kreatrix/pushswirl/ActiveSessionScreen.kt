package org.kreatrix.pushswirl

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(viewModel: SessionViewModel) {
    // Traverse ContextWrapper chain to find the real Activity — direct cast can fail
    // when Android recreates the Activity after a system config change (theme, locale, etc.)
    val context = LocalContext.current
    val window = remember(context) {
        var ctx: Context = context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        (ctx as Activity).window
    }
    // Include `window` as a key so the effect re-runs if the Activity is recreated,
    // preventing FLAG_KEEP_SCREEN_ON from being applied to a stale/destroyed window.
    DisposableEffect(window, viewModel.keepScreenOn) {
        if (viewModel.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showCancelDialog by remember { mutableStateOf(false) }
    val isTagNote = viewModel.sessionState is SessionState.TagNoteInput

    BackHandler {
        if (isTagNote) viewModel.confirmTagNote(emptyList(), "")
        else showCancelDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Session", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    TextButton(onClick = {
                        if (isTagNote) viewModel.confirmTagNote(emptyList(), "")
                        else showCancelDialog = true
                    }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = viewModel.sessionState) {
                    is SessionState.TTD -> TTDView(viewModel, state.phase, isWideScreen = isWideScreen)
                    is SessionState.DepthInput -> DepthInputView(viewModel, state.phase, isWideScreen = isWideScreen)
                    is SessionState.Dilation -> DilationView(viewModel, state.phase, state.action, isWideScreen = isWideScreen)
                    is SessionState.TagNoteInput -> TagNoteView(viewModel, isWideScreen = isWideScreen)
                    else -> {}
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Session?") },
            text = { Text("What would you like to do with the current session?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text("Continue Session")
                    }
                    TextButton(
                        onClick = {
                            viewModel.saveAndCancelSession()
                            showCancelDialog = false
                        }
                    ) {
                        Text("Save & Exit")
                    }
                    TextButton(
                        onClick = {
                            viewModel.cancelSession()
                            showCancelDialog = false
                        }
                    ) {
                        Text("Discard Session", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}

@Composable
fun TTDView(viewModel: SessionViewModel, phase: PhaseSize, isWideScreen: Boolean = false) {
    val blinded = viewModel.sessionConfig.blindedTtdTimer
    val ttdDisplay = if (blinded) formatTimeBlinded(viewModel.ttdSeconds) else formatTime(viewModel.ttdSeconds)

    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = phase.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Time to Depth",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = ttdDisplay,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TTDButtons(viewModel)
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = phase.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Time to Depth",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = ttdDisplay,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(48.dp))
            TTDButtons(viewModel)
        }
    }
}

@Composable
private fun TTDButtons(viewModel: SessionViewModel) {
    if (viewModel.ttdRunning) {
        Button(
            onClick = { viewModel.pauseTTD() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Pause", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.finishTTD() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("Depth reached", fontSize = 18.sp)
        }
    } else {
        if (viewModel.ttdSeconds > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.startTTD() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Resume", fontSize = 18.sp)
            }
        } else {
            Button(
                onClick = { viewModel.startTTD() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun DepthInputView(viewModel: SessionViewModel, phase: PhaseSize, isWideScreen: Boolean = false) {
    var depthText by remember {
        mutableStateOf(
            if (viewModel.currentDepthInput % 1 == 0f) {
                viewModel.currentDepthInput.toInt().toString()
            } else {
                viewModel.currentDepthInput.toString()
            }
        )
    }

    val isValidDepth = depthText.isNotEmpty() &&
            depthText.toFloatOrNull() != null &&
            depthText.toFloatOrNull()!! in 0.1f..99.9f

    val onValueChange: (String) -> Unit = { newValue ->
        val filtered = newValue.filter { it.isDigit() || it == '.' }
        val dotCount = filtered.count { it == '.' }
        if (dotCount <= 1) {
            val parts = filtered.split('.')
            val isValid = when {
                parts.size == 1 -> true
                parts.size == 2 -> parts[1].length <= 1
                else -> false
            }
            if (isValid) {
                depthText = filtered
                val depth = filtered.toFloatOrNull()
                if (depth != null && depth in 0.1f..99.9f) {
                    viewModel.updateDepthInput(depth)
                }
            }
        }
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = phase.name,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Record Depth",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = depthText,
                    onValueChange = onValueChange,
                    label = { Text("Depth (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter the depth reached in centimeters (e.g., 14.5)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.confirmDepth() },
                    enabled = isValidDepth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Continue", fontSize = 18.sp)
                }
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = phase.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Record Depth",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = depthText,
                onValueChange = onValueChange,
                label = { Text("Depth (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter the depth reached in centimeters (e.g., 14.5)",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = { viewModel.confirmDepth() },
                enabled = isValidDepth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Continue", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun DilationView(viewModel: SessionViewModel, phase: PhaseSize, action: DilationAction, isWideScreen: Boolean = false) {
    var showEarlyFinishDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showVibrationDialog by remember { mutableStateOf(false) }

    val isStatic = viewModel.sessionConfig.actionTime == 0
    val progress = 1f - (viewModel.dilationRemainingSeconds.toFloat() / viewModel.dilationTotalSeconds.toFloat())

    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = phase.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (viewModel.inActionPause) "Break" else action.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (action == DilationAction.PUSH)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                DilationTimerDisplay(viewModel, isStatic, isWideScreen = true)
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DilationControls(
                    viewModel = viewModel,
                    onShowVibrationDialog = { showVibrationDialog = true },
                    onShowSoundDialog = { showSoundDialog = true },
                    onShowEarlyFinishDialog = { showEarlyFinishDialog = true }
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = phase.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (viewModel.inActionPause) "Break" else action.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (action == DilationAction.PUSH)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            DilationTimerDisplay(viewModel, isStatic)
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(modifier = Modifier.height(32.dp))
            DilationControls(
                viewModel = viewModel,
                onShowVibrationDialog = { showVibrationDialog = true },
                onShowSoundDialog = { showSoundDialog = true },
                onShowEarlyFinishDialog = { showEarlyFinishDialog = true }
            )
        }
    }

    if (showEarlyFinishDialog) {
        AlertDialog(
            onDismissRequest = { showEarlyFinishDialog = false },
            title = { Text("Finish Early?") },
            text = {
                Text(
                    "End this dilation phase now with ${formatTime(viewModel.dilationRemainingSeconds.toLong())} remaining? " +
                            "The phase will be recorded with the actual time completed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.earlyFinishDilation()
                        showEarlyFinishDialog = false
                    }
                ) {
                    Text("Finish Early", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEarlyFinishDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }

    if (showSoundDialog) {
        SoundSettingsDialog(
            settings = viewModel.notificationSettings,
            onSettingsChange = { viewModel.updateNotificationSettings(it) },
            onDismiss = { showSoundDialog = false },
            isWideScreen = isWideScreen
        )
    }

    if (showVibrationDialog) {
        VibrationSettingsDialog(
            settings = viewModel.notificationSettings,
            onSettingsChange = { viewModel.updateNotificationSettings(it) },
            onDismiss = { showVibrationDialog = false },
            isWideScreen = isWideScreen
        )
    }
}

@Composable
private fun DilationTimerDisplay(viewModel: SessionViewModel, isStatic: Boolean, isWideScreen: Boolean = false) {
    if (isStatic) {
        Text(
            text = formatTime(viewModel.dilationRemainingSeconds.toLong()),
            fontSize = if (isWideScreen) 52.sp else 72.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Remaining",
            fontSize = if (isWideScreen) 13.sp else 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    } else if (isWideScreen) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime((if (viewModel.inActionPause) viewModel.actionPauseRemainingSeconds else viewModel.actionRemainingSeconds).toLong()),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (viewModel.inActionPause) "Break" else "Activity Timer",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(viewModel.dilationRemainingSeconds.toLong()),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Total Remaining",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        Text(
            text = formatTime((if (viewModel.inActionPause) viewModel.actionPauseRemainingSeconds else viewModel.actionRemainingSeconds).toLong()),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (viewModel.inActionPause) "Break" else "Activity Timer",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = formatTime(viewModel.dilationRemainingSeconds.toLong()),
            fontSize = 36.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Total Remaining",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun DilationControls(
    viewModel: SessionViewModel,
    onShowVibrationDialog: () -> Unit,
    onShowSoundDialog: () -> Unit,
    onShowEarlyFinishDialog: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onShowVibrationDialog,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewModel.notificationSettings.vibrationEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (viewModel.notificationSettings.vibrationEnabled)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("Vibration")
        }
        Button(
            onClick = onShowSoundDialog,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewModel.notificationSettings.soundEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (viewModel.notificationSettings.soundEnabled)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("Sound")
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { viewModel.toggleDilationPause() },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (viewModel.dilationPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            if (viewModel.dilationPaused) "Resume" else "Pause",
            fontSize = 18.sp
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onShowEarlyFinishDialog,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Text("Early Finish", fontSize = 18.sp)
    }
}

@Composable
private fun SoundSettingsDialog(
    settings: NotificationSettings,
    onSettingsChange: (NotificationSettings) -> Unit,
    onDismiss: () -> Unit,
    isWideScreen: Boolean = false
) {
    var local by remember { mutableStateOf(settings) }

    val soundModes = listOf(
        SoundMode.OFF to "Off",
        SoundMode.SYSTEM to "On - System Setting",
        SoundMode.MANUAL to "On - Manual Volume"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = if (isWideScreen) Modifier.widthIn(min = 420.dp) else Modifier,
        properties = if (isWideScreen) DialogProperties(usePlatformDefaultWidth = false) else DialogProperties(),
        title = { Text("Sound") },
        text = {
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        soundModes.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = local.soundMode == mode,
                                    onClick = { local = local.copy(soundMode = mode) }
                                )
                                Text(label, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (local.soundMode == SoundMode.MANUAL) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Volume", fontSize = 14.sp)
                                Text("${(local.volumeLevel * 100).roundToInt()}%", fontSize = 14.sp)
                            }
                            Slider(
                                value = local.volumeLevel,
                                onValueChange = { local = local.copy(volumeLevel = it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (local.soundMode != SoundMode.OFF) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = local.switchBeepsEnabled,
                                    onCheckedChange = { local = local.copy(switchBeepsEnabled = it) }
                                )
                                Text("Switch beeps", modifier = Modifier.padding(start = 4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = local.phaseFanfareEnabled,
                                    onCheckedChange = { local = local.copy(phaseFanfareEnabled = it) }
                                )
                                Text("Phase fanfare", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    soundModes.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = local.soundMode == mode,
                                onClick = { local = local.copy(soundMode = mode) }
                            )
                            Text(label, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    if (local.soundMode == SoundMode.MANUAL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Volume", fontSize = 14.sp)
                            Text("${(local.volumeLevel * 100).roundToInt()}%", fontSize = 14.sp)
                        }
                        Slider(
                            value = local.volumeLevel,
                            onValueChange = { local = local.copy(volumeLevel = it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (local.soundMode != SoundMode.OFF) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = local.switchBeepsEnabled,
                                onCheckedChange = { local = local.copy(switchBeepsEnabled = it) }
                            )
                            Text("Switch beeps", modifier = Modifier.padding(start = 4.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = local.phaseFanfareEnabled,
                                onCheckedChange = { local = local.copy(phaseFanfareEnabled = it) }
                            )
                            Text("Phase fanfare", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSettingsChange(local)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun VibrationSettingsDialog(
    settings: NotificationSettings,
    onSettingsChange: (NotificationSettings) -> Unit,
    onDismiss: () -> Unit,
    isWideScreen: Boolean = false
) {
    var local by remember { mutableStateOf(settings) }

    val vibrationModes = listOf(
        VibrationMode.OFF to "Off",
        VibrationMode.SYSTEM to "On - System Setting",
        VibrationMode.MANUAL to "On - Manual Amplitude"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = if (isWideScreen) Modifier.widthIn(min = 420.dp) else Modifier,
        properties = if (isWideScreen) DialogProperties(usePlatformDefaultWidth = false) else DialogProperties(),
        title = { Text("Vibration") },
        text = {
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        vibrationModes.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = local.vibrationMode == mode,
                                    onClick = { local = local.copy(vibrationMode = mode) }
                                )
                                Text(label, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (local.vibrationMode == VibrationMode.MANUAL) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Amplitude", fontSize = 14.sp)
                                Text("${(local.vibrationAmplitude * 100).roundToInt()}%", fontSize = 14.sp)
                            }
                            Slider(
                                value = local.vibrationAmplitude,
                                onValueChange = { local = local.copy(vibrationAmplitude = it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    vibrationModes.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = local.vibrationMode == mode,
                                onClick = { local = local.copy(vibrationMode = mode) }
                            )
                            Text(label, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    if (local.vibrationMode == VibrationMode.MANUAL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Amplitude", fontSize = 14.sp)
                            Text("${(local.vibrationAmplitude * 100).roundToInt()}%", fontSize = 14.sp)
                        }
                        Slider(
                            value = local.vibrationAmplitude,
                            onValueChange = { local = local.copy(vibrationAmplitude = it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSettingsChange(local)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TagNoteView(viewModel: SessionViewModel, isWideScreen: Boolean = false) {
    var tagIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var note   by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Session Complete",
            fontSize = if (isWideScreen) 24.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (viewModel.tags.isNotEmpty()) {
            Text(
                text = "Tags",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                viewModel.tags.forEach { tag ->
                    val isSelected = tag.id in tagIds
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            tagIds = if (isSelected) tagIds - tag.id else tagIds + tag.id
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
            Spacer(modifier = Modifier.height(24.dp))
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = { viewModel.confirmTagNote(tagIds, note.trim()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Done", fontSize = 18.sp)
        }
    }
}

private fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}

private fun formatTimeBlinded(seconds: Long): String {
    val secs = seconds % 60
    return String.format("??:%02d", secs)
}
