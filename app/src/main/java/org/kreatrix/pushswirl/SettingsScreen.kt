package org.kreatrix.pushswirl

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    val isWideScreen = LocalIsWideScreen.current
    val sectionTitles = listOf("Progress", "Countdown", "Tags", "App", "Sounds")

    val intervalMinutesTotal = viewModel.countdownIntervalMinutes
    var hoursText by remember(intervalMinutesTotal) { mutableStateOf((intervalMinutesTotal / 60).toString()) }
    var minutesText by remember(intervalMinutesTotal) { mutableStateOf((intervalMinutesTotal % 60).toString()) }
    // null = show the section list (phone only); wide screens always render a section.
    var selectedTab by remember { mutableStateOf<Int?>(null) }

    // On phones a Back returns from an open section to the list before leaving Settings.
    val sectionOpen = !isWideScreen && selectedTab != null
    BackHandler {
        if (sectionOpen) selectedTab = null
        else viewModel.navigateTo(AppScreen.Home)
    }

    fun commitInterval() {
        val h = hoursText.toIntOrNull()?.coerceIn(0, 99) ?: 0
        val m = minutesText.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val total = h * 60 + m
        if (total > 0) viewModel.updateCountdownIntervalMinutes(total)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (sectionOpen) sectionTitles[selectedTab!!] else "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    TextButton(onClick = {
                        if (sectionOpen) selectedTab = null
                        else viewModel.navigateTo(AppScreen.Home)
                    }) {
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
            @Composable
            fun TabContent(section: Int) {
                when (section) {
                    0 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        var showDay0Picker by remember { mutableStateOf(false) }
                        var showMilestonePicker by remember { mutableStateOf(false) }
                        var pendingMilestoneDate by remember { mutableStateOf<Long?>(null) }
                        var pendingComment by remember { mutableStateOf("") }
                        var editingMilestone by remember { mutableStateOf<Milestone?>(null) }
                        var editingComment by remember { mutableStateOf("") }
                        val day0Date = viewModel.day0Date
                        val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

                        if (day0Date == null) {
                            Button(
                                onClick = { showDay0Picker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Set Day 0", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Day when it all started",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))) {
                                        append("Day 0: ")
                                    }
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                        append(dateFormat.format(Date(day0Date)))
                                    }
                                },
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { showDay0Picker = true }) {
                                    Text("Select date")
                                }
                                OutlinedButton(onClick = { viewModel.updateDay0Date(null) }) {
                                    Text("Forget date")
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Milestones",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            viewModel.milestones.forEach { milestone ->
                                val dayNumber = ((milestone.date - day0Date) / (1000L * 60 * 60 * 24)).toInt()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))) {
                                                    append("Day $dayNumber: ")
                                                }
                                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                                    append(dateFormat.format(Date(milestone.date)))
                                                }
                                            },
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (milestone.comment.isNotBlank()) {
                                            Text(
                                                text = milestone.comment,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    TextButton(onClick = {
                                        editingMilestone = milestone
                                        editingComment = milestone.comment
                                    }) {
                                        Text("Edit")
                                    }
                                    TextButton(onClick = { viewModel.removeMilestone(milestone) }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showMilestonePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add milestone")
                            }
                        }

                        if (showDay0Picker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = day0Date
                                    ?.let { localMidnightToUtcMidnight(it) }
                                    ?: System.currentTimeMillis()
                            )
                            DatePickerDialog(
                                onDismissRequest = { showDay0Picker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        datePickerState.selectedDateMillis
                                            ?.let { viewModel.updateDay0Date(utcMidnightToLocalMidnight(it)) }
                                        showDay0Picker = false
                                    }) { Text("OK") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDay0Picker = false }) { Text("Cancel") }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }

                        if (showMilestonePicker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = System.currentTimeMillis()
                            )
                            DatePickerDialog(
                                onDismissRequest = { showMilestonePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        datePickerState.selectedDateMillis?.let {
                                            pendingMilestoneDate = utcMidnightToLocalMidnight(it)
                                            pendingComment = ""
                                        }
                                        showMilestonePicker = false
                                    }) { Text("Next") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showMilestonePicker = false }) { Text("Cancel") }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }

                        if (pendingMilestoneDate != null) {
                            AlertDialog(
                                onDismissRequest = { pendingMilestoneDate = null },
                                title = { Text("Add comment") },
                                text = {
                                    OutlinedTextField(
                                        value = pendingComment,
                                        onValueChange = { pendingComment = it },
                                        label = { Text("Comment (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.addMilestone(Milestone(pendingMilestoneDate!!, pendingComment.trim()))
                                        pendingMilestoneDate = null
                                    }) { Text("Add") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingMilestoneDate = null }) { Text("Cancel") }
                                }
                            )
                        }

                        if (editingMilestone != null) {
                            AlertDialog(
                                onDismissRequest = { editingMilestone = null },
                                title = { Text("Edit comment") },
                                text = {
                                    OutlinedTextField(
                                        value = editingComment,
                                        onValueChange = { editingComment = it },
                                        label = { Text("Comment (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.updateMilestoneComment(editingMilestone!!, editingComment.trim())
                                        editingMilestone = null
                                    }) { Text("Save") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { editingMilestone = null }) { Text("Cancel") }
                                }
                            )
                        }
                    }

                    1 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                    }

                    2 -> TagsTabContent(viewModel)

                    3 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))

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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Keep CPU awake", fontSize = 16.sp)
                                Text(
                                    "Hold a wake lock during active session to prevent timers from freezing when the screen is off",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Checkbox(
                                checked = viewModel.wakeLockEnabled,
                                onCheckedChange = { viewModel.updateWakeLockEnabled(it) }
                            )
                        }
                    }
                    4 -> SoundsTabContent(viewModel)
                }
            }

            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                    ) {
                        sectionTitles.forEachIndexed { i, title ->
                            NavigationDrawerItem(
                                label = { Text(title) },
                                selected = (selectedTab ?: 0) == i,
                                onClick = { selectedTab = i },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TabContent(selectedTab ?: 0)
                    }
                }
            } else {
                val openSection = selectedTab
                if (openSection == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        sectionTitles.forEachIndexed { i, title ->
                            ListItem(
                                headlineContent = { Text(title) },
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.clickable { selectedTab = i }
                            )
                            HorizontalDivider()
                        }
                    }
                } else {
                    TabContent(openSection)
                }
            }
        }
    }
}

@Composable
private fun SoundsTabContent(viewModel: SessionViewModel) {
    val context = LocalContext.current
    val settings = viewModel.notificationSettings

    var pendingSound by remember { mutableStateOf<AppSound?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun releasePermission(uriStr: String?) {
        uriStr ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriStr), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val sound = pendingSound
        pendingSound = null
        if (uri != null && sound != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // Release the previously-held grant for this slot, if any, before replacing it.
            releasePermission(settings.customUriFor(sound))
            viewModel.updateNotificationSettings(settings.withCustomUri(sound, uri.toString()))
        }
    }

    fun play(sound: AppSound) {
        player?.release()
        player = playAppSoundPreview(context, sound, viewModel.notificationSettings)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Sounds",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AppSound.entries.forEachIndexed { index, sound ->
            val isCustom = settings.customUriFor(sound) != null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(sound.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (isCustom) "Custom sound" else "Default sound",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { play(sound) }) { Text("Play") }
                    OutlinedButton(onClick = {
                        pendingSound = sound
                        picker.launch(arrayOf("audio/*"))
                    }) { Text("Set own sound") }
                    TextButton(
                        onClick = {
                            releasePermission(settings.customUriFor(sound))
                            viewModel.updateNotificationSettings(settings.withCustomUri(sound, null))
                        },
                        enabled = isCustom
                    ) { Text("Reset") }
                }
            }
            if (index < AppSound.entries.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun TagsTabContent(viewModel: SessionViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var pendingDeleteTag by remember { mutableStateOf<Tag?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Tags",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        viewModel.tags.forEach { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(tag.color.toComposeColor())
                    )
                    Text(tag.name, fontSize = 16.sp)
                }
                Row {
                    TextButton(onClick = {
                        editingTag = tag
                        showDialog = true
                    }) {
                        Text("Edit")
                    }
                    TextButton(onClick = { pendingDeleteTag = tag }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                editingTag = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add tag")
        }
    }

    if (showDialog) {
        TagDialog(
            initial = editingTag,
            onDismiss = { showDialog = false },
            onSave = { tag ->
                if (editingTag == null) viewModel.addTag(tag)
                else viewModel.updateTag(tag)
                showDialog = false
            }
        )
    }

    if (pendingDeleteTag != null) {
        val errorColor = MaterialTheme.colorScheme.error
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("Remove tag") },
            text = {
                Text(buildAnnotatedString {
                    append("Remove \"${pendingDeleteTag!!.name}\"?\n\n")
                    withStyle(SpanStyle(color = errorColor)) {
                        append("This tag will be removed from all sessions.")
                    }
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeTag(pendingDeleteTag!!)
                    pendingDeleteTag = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TagDialog(
    initial: Tag?,
    onDismiss: () -> Unit,
    onSave: (Tag) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var color by remember(initial) { mutableStateOf(initial?.color ?: TagColor.RED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add tag" else "Edit tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TagColor.entries.forEach { tagColor ->
                        val isSelected = color == tagColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(tagColor.toComposeColor())
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outline
                                )
                                .clickable { color = tagColor }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            if (initial == null) Tag(name = name.trim(), color = color)
                            else initial.copy(name = name.trim(), color = color)
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun TagColor.toComposeColor(): Color = when (this) {
    TagColor.RED    -> Color(0xFFE53935)
    TagColor.ORANGE -> Color(0xFFFB8C00)
    TagColor.GREEN  -> Color(0xFF43A047)
    TagColor.BLUE   -> Color(0xFF1E88E5)
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
