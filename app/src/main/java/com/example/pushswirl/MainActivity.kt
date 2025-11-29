package com.example.pushswirl

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

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

        setContent {
            PushSwirlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PushSwirlApp()
                }
            }
        }
    }
}

@Composable
fun PushSwirlApp() {
    val viewModel: SessionViewModel = viewModel()

    when (viewModel.currentScreen) {
        is AppScreen.Home -> HomeScreen(viewModel)
        is AppScreen.NewSession -> NewSessionScreen(viewModel)
        is AppScreen.ActiveSession -> ActiveSessionScreen(viewModel)
        is AppScreen.SessionHistory -> SessionHistoryScreen(viewModel)
        is AppScreen.Statistics -> StatisticsScreen(viewModel)
    }
}