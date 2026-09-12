package org.kreatrix.pushswirl

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch

/** Whether the current window is at least medium width (>= 600dp) — the app's wide/landscape breakpoint. */
val LocalIsWideScreen = staticCompositionLocalOf { false }

/** Whether the app is currently shrunk into a picture-in-picture window. */
val LocalIsInPip = staticCompositionLocalOf { false }

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()
    private var isInPip by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Build the ViewModel here, before the snapshotFlow below reads it: snapshotFlow
        // evaluates its block in a read-only snapshot, and constructing the ViewModel writes
        // Compose state (pendingResume), which such a snapshot rejects.
        val session = viewModel

        // Android 12+ enters PiP by itself when leaving the app, but only if the params say so,
        // so keep them in step with whether there is a session worth keeping on screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lifecycleScope.launch {
                snapshotFlow { session.pipEnabled && session.isSessionActive }
                    .collect { shouldAutoEnter -> applyPipParams(shouldAutoEnter) }
            }
        }

        setContent {
            PushSwirlApp(viewModel, isInPip)
        }
    }

    private fun pipSupported() =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter)
            }
            .build()

    private fun applyPipParams(autoEnter: Boolean) {
        if (!pipSupported()) return
        try {
            setPictureInPictureParams(pipParams(autoEnter))
        } catch (e: IllegalStateException) {
            // PiP turned off for this app in system settings.
        }
    }

    /**
     * Entry path for Android 11 and older. From 12 on, auto-enter does it: onUserLeaveHint is not
     * called reliably under gesture navigation, and an explicit request during a transition
     * auto-enter already owns is refused anyway.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!viewModel.pipEnabled || !viewModel.isSessionActive || isInPip) return
        if (!pipSupported()) return
        try {
            enterPictureInPictureMode(pipParams(autoEnter = false))
        } catch (e: IllegalStateException) {
            // PiP turned off for this app in system settings, or not allowed right now.
        }
    }

    override fun onPictureInPictureModeChanged(
        inPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig)
        isInPip = inPictureInPictureMode
    }
}

@Composable
fun PushSwirlApp(viewModel: SessionViewModel, isInPip: Boolean = false) {
    PushSwirlTheme(themeMode = viewModel.themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val isWide = currentWindowAdaptiveInfo().windowSizeClass
                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
            CompositionLocalProvider(
                LocalIsWideScreen provides (isWide && !isInPip),
                LocalIsInPip provides isInPip
            ) {
                // The PiP window is a fraction of the screen, so it gets its own stripped-down
                // layout rather than a miniature of the full session screen.
                if (isInPip) {
                    PipSessionView(viewModel)
                    return@CompositionLocalProvider
                }
                when (viewModel.currentScreen) {
                    is AppScreen.Home -> HomeScreen(viewModel)
                    is AppScreen.NewSession -> NewSessionScreen(viewModel)
                    is AppScreen.ActiveSession -> ActiveSessionScreen(viewModel)
                    is AppScreen.SessionHistory -> SessionHistoryScreen(viewModel)
                    is AppScreen.EditSession -> EditSessionScreen(viewModel)
                    is AppScreen.Statistics -> StatisticsScreen(viewModel)
                    is AppScreen.StatsFilter -> StatsFilterScreen(viewModel)
                    is AppScreen.Settings -> SettingsScreen(viewModel)
                    is AppScreen.ImportExport -> ImportExportScreen(viewModel)
                }
            }
        }
    }
}