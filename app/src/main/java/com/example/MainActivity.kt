@file:Suppress("DEPRECATION")
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*
import com.example.ui.theme.JarvisDeepBlue
import com.example.ui.theme.JarvisCardBlue
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisGlowGreen
import com.example.ui.theme.JarvisRiskRed
import com.example.ui.theme.JarvisTextWhite
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Greet the user when opening the app
        com.example.accessibility.JarvisAccessibilityManager.speak("Hey Anvin, what's up")

        // Start Foreground Service for quick access
        try {
            com.example.ui.popup.JarvisForegroundService.start(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setContent {
            MyApplicationTheme {
                val viewModel: JarvisViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                            android.util.Log.d("MainActivity", "Activity stopped; pausing microphone listening")
                            viewModel.voiceController.stopListening()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                JarvisDashboardMain(viewModel)
            }
        }
    }
}

@Composable
fun JarvisDashboardMain(viewModel: JarvisViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    
    val showConfirmation by viewModel.showConfirmationDialog.collectAsState()
    val confirmationMessage by viewModel.confirmationMessage.collectAsState()

    // Alert dialog guardian for risky actions
    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = {
                Text(
                    text = "JARVIS GUARD AUTHORIZATION",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisNeonCyan
                )
            },
            text = {
                Text(
                    text = confirmationMessage,
                    color = JarvisTextWhite,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisGlowGreen,
                        contentColor = JarvisDeepBlue
                    )
                ) {
                    Text("AUTHORIZE ACTION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelPendingAction() },
                    colors = ButtonDefaults.textButtonColors(contentColor = JarvisRiskRed)
                ) {
                    Text("ABORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = JarvisCardBlue
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlue)
            .statusBarsPadding()
            .navigationBarsPadding(),
        bottomBar = {
            NavigationBar(
                containerColor = JarvisCardBlue,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home Core Dashboard") },
                    label = { Text("CORE", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisDeepBlue,
                        selectedTextColor = JarvisNeonCyan,
                        indicatorColor = JarvisNeonCyan,
                        unselectedIconColor = JarvisNeonCyan.copy(alpha = 0.5f),
                        unselectedTextColor = JarvisNeonCyan.copy(alpha = 0.5f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Onboarding Permissions Settings") },
                    label = { Text("PERMS", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisDeepBlue,
                        selectedTextColor = JarvisNeonCyan,
                        indicatorColor = JarvisNeonCyan,
                        unselectedIconColor = JarvisNeonCyan.copy(alpha = 0.5f),
                        unselectedTextColor = JarvisNeonCyan.copy(alpha = 0.5f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Historical Activity Logs") },
                    label = { Text("LOGS", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisDeepBlue,
                        selectedTextColor = JarvisNeonCyan,
                        indicatorColor = JarvisNeonCyan,
                        unselectedIconColor = JarvisNeonCyan.copy(alpha = 0.5f),
                        unselectedTextColor = JarvisNeonCyan.copy(alpha = 0.5f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "System Settings configurations") },
                    label = { Text("SETTINGS", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisDeepBlue,
                        selectedTextColor = JarvisNeonCyan,
                        indicatorColor = JarvisNeonCyan,
                        unselectedIconColor = JarvisNeonCyan.copy(alpha = 0.5f),
                        unselectedTextColor = JarvisNeonCyan.copy(alpha = 0.5f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel = viewModel, onTabSelect = { selectedTab = it })
                1 -> PermissionsScreen(viewModel = viewModel)
                2 -> HistoryScreen(viewModel = viewModel)
                3 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
