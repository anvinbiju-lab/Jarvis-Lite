@file:Suppress("DEPRECATION")
package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CommandLog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- HUD Pulsing core generator ---
@Composable
fun JarvisCoreCircle(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = if (isListening) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 600 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radius"
    )

    val coreRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(170.dp)
            .drawBehind {
                val radius = size.minDimension / 2
                // Pulse waves
                drawCircle(
                    color = JarvisNeonCyan.copy(alpha = if (isListening) 0.25f else 0.1f),
                    radius = radius * pulseScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = JarvisElectricBlue.copy(alpha = if (isListening) 0.15f else 0.05f),
                    radius = radius * (pulseScale + 0.15f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 3.2f
            
            // Outer HUD Tech dashboard lines
            drawCircle(
                brush = Brush.sweepGradient(listOf(JarvisNeonCyan, Color.Transparent, JarvisElectricBlue, JarvisNeonCyan)),
                radius = radius * 1.2f,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Dynamic core dots
            drawCircle(
                color = if (isListening) JarvisGlowGreen else JarvisNeonCyan,
                radius = radius * 0.75f,
                center = center,
                style = Stroke(width = 8.dp.toPx(), miter = 1f)
            )
        }
        
        Icon(
            imageVector = if (isListening) Icons.Filled.KeyboardVoice else Icons.Filled.Mic,
            contentDescription = "Microphone Trigger Button",
            tint = if (isListening) JarvisGlowGreen else JarvisNeonCyan,
            modifier = Modifier.size(52.dp)
        )
    }
}

// --- Home / Assistant Screen Layout ---
@Composable
fun HomeScreen(
    viewModel: JarvisViewModel,
    onTabSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val currentCommand by viewModel.currentCommandText.collectAsState()
    val isListening by viewModel.voiceController.isListening.collectAsState()
    val voiceError by viewModel.voiceController.error.collectAsState()
    val pipelineStatus by viewModel.activePipelineStatus.collectAsState()
    val isConnected by viewModel.isAccessibilityConnected.collectAsState()
    val latestLog by viewModel.executionLog.collectAsState()

    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Holographic Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBlue.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, JarvisTerminalGrey)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) JarvisGlowGreen else JarvisRiskRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "SERVICE ONLINE" else "SERVICE OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isConnected) JarvisGlowGreen else JarvisRiskRed
                    )
                }

                if (!isConnected) {
                    TextButton(
                        onClick = { onTabSelect(1) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("CONFIGURE", fontSize = 11.sp, color = JarvisNeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // JARVIS central core trigger
        Text(
            text = "AGENT SYSTEM CORE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = JarvisTextMuted,
            letterSpacing = 2.sp
        )

        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .clickable { viewModel.triggerMic() }
                .testTag("floating_mic_button"),
            contentAlignment = Alignment.Center
        ) {
            JarvisCoreCircle(isListening = isListening)
        }

        Text(
            text = if (isListening) "LISTENING..." else "TAP CORE TO SPEAK",
            color = if (isListening) JarvisGlowGreen else JarvisNeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        voiceError?.let { err ->
            Text(
                text = err,
                color = JarvisGlowOrange,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Status displays
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBlue),
            border = BorderStroke(1.dp, JarvisTerminalGrey)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "RECOGNIZED SPEECH INPUT",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextMuted
                )
                Text(
                    text = currentCommand.ifEmpty { "(Say something like \"Open YouTube\" or use mock buttons)" },
                    fontSize = 14.sp,
                    color = JarvisTextWhite,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("recognized_command_text")
                )

                HorizontalDivider(color = JarvisTerminalGrey, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "PIPELINE PROGRESS LOG",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextMuted
                )
                Text(
                    text = pipelineStatus,
                    fontSize = 13.sp,
                    color = JarvisNeonCyan,
                    fontFamily = FontFamily.Monospace
                )
                if (latestLog.isNotEmpty()) {
                    Text(
                        text = "Result: $latestLog",
                        fontSize = 11.sp,
                        color = JarvisTextWhite.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Manual Fallback Entry Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBlue.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, JarvisTerminalGrey.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "KEYBOARD FALLBACK TEST PANEL",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JarvisTextMuted,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_input_fallback"),
                        placeholder = { Text("Type any command...", color = JarvisTextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextWhite,
                            unfocusedTextColor = JarvisTextWhite,
                            focusedBorderColor = JarvisNeonCyan,
                            unfocusedBorderColor = JarvisTerminalGrey,
                            cursorColor = JarvisNeonCyan
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotEmpty()) {
                                viewModel.simulateCommand(textInput)
                                textInput = ""
                            }
                        })
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotEmpty()) {
                                viewModel.simulateCommand(textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .background(JarvisElectricBlue, RoundedCornerShape(8.dp))
                            .size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send Text Command", tint = Color.White)
                    }
                }
            }
        }

        // Frictionless Simulated Preset Buttons Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBlue),
            border = BorderStroke(1.dp, JarvisTerminalGrey)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RAPID PRESET DEMO COMMANDS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextMuted
                )

                val presets = listOf(
                    "Open YouTube",
                    "Search YouTube for Malayalam songs",
                    "Scroll down",
                    "Open Instagram",
                    "Go to chats",
                    "Open chat with Anvin",
                    "Type hello, how are you?",
                    "Volume up",
                    "Go back",
                    "Go home"
                )

                FlowRowWrapper(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    presets.forEach { text ->
                        Button(
                            onClick = { viewModel.simulateCommand(text) },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisTerminalGrey),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(text = text, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = JarvisTextWhite)
                        }
                    }
                }
            }
        }
    }
}

// --- Permissions Onboarding Checklist Screen Layout ---
@Composable
fun PermissionsScreen(
    viewModel: JarvisViewModel
) {
    val context = LocalContext.current
    val isConnected by viewModel.isAccessibilityConnected.collectAsState()
    
    // Check overlay state locally
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    // Check record audio state
    val hostActivity = context as? androidx.activity.ComponentActivity
    var isAudioGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val recordAudioLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isAudioGranted = granted
    }

    // Check notifications state
    var isNotificationGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    // Check/request Bluetooth Connect state (Android 12+)
    var isBluetoothConnectGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val bluetoothConnectLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isBluetoothConnectGranted = granted
    }

    // Check/request Media Storage state
    var isStorageGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_MEDIA_IMAGES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val storageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isStorageGranted = granted
    }

    // Refresh states when returning back to screen
    LaunchedEffect(Unit) {
        isOverlayGranted = Settings.canDrawOverlays(context)
        isAudioGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            isNotificationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            isBluetoothConnectGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        isStorageGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SYSTEM ACCESS AUTHORIZATION",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = JarvisTextMuted,
            letterSpacing = 1.5.sp
        )

        Text(
            text = "Jarvis Lite utilizes secure local Android system APIs to control screen features on your behalf. These authorizations run 100% locally on this device.",
            fontSize = 13.sp,
            color = JarvisTextWhite.copy(alpha = 0.8f)
        )

        // Permission Card 1: Accessibility Service (MANDATORY)
        PermissionRow(
            title = "1. Accessibility Automation Service",
            description = "Mandatory. Allows Jarvis to launch authorized apps, perform scrolls, click named components by text, and type inputs on your behalf.\n\nInstructions: Tap \"CONFIGURE\", locate \"Jarvis Lite\" under Downloaded Apps, and toggle the permission.",
            isGranted = isConnected,
            onConfigure = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }
        )

        // Permission Card 2: Record Audio (Optional for Voice input)
        PermissionRow(
            title = "2. Speech Microphone Authorization",
            description = "Required to speak commands aloud. Converts voice to native text 100% client-side with no audio saved or transmitted.",
            isGranted = isAudioGranted,
            onConfigure = {
                recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        )

        // Permission Card 3: Draw Overlays (Draw status alerts over other apps)
        PermissionRow(
            title = "3. Overlay Alert Draws",
            description = "Optional. Allows Jarvis Lite to draw an interactive floating bubble stating the current automation pipeline progress (Listening, Executing, Failed) over third-party applications like YouTube and Instagram.",
            isGranted = isOverlayGranted,
            onConfigure = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        )

        // Permission Card 4: Persistent Notification
        PermissionRow(
            title = "4. Persistent Background Invocation",
            description = "Allows Jarvis to pin an awake notification globally so you can summon it from anywhere. (Alternatively, set Jarvis as your Default Assistant to summon via the power button).",
            isGranted = isNotificationGranted,
            onConfigure = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        // Permission Card 5: Set as Default Assistant (Alternative Wake Method)
        PermissionRow(
            title = "5. Set as Default Assistant (Power Button Wake)",
            description = "Instead of continuous microphone listening (which causes battery drain and beep noises), set Jarvis as your Default Assistant to wake it up via long-pressing the power button or swiping diagonally from screen corners.",
            isGranted = false, // We can't synchronously check default assistant without complex queries, usually just an action button
            onConfigure = {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                context.startActivity(intent)
            }
        )

        // Permission Card 6: Bluetooth Connect Authorization (Android 12+)
        PermissionRow(
            title = "6. Bluetooth Connect & Control Status",
            description = "Required to turn Bluetooth on and off on your behalf. Toggles Bluetooth power state locally with non-invasive API support.",
            isGranted = isBluetoothConnectGranted,
            onConfigure = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    bluetoothConnectLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        )

        // Permission Card 7: Local Media Storage & File Access
        PermissionRow(
            title = "7. Media Storage & Files Search Locator",
            description = "Required to allow searches like 'find billing invoice' or 'search receipt' inside your local system directories to pinpoint exact matches.",
            isGranted = isStorageGranted,
            onConfigure = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    storageLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    storageLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        )

        // Educational notice inside layout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBlue),
            border = BorderStroke(1.dp, JarvisTerminalGrey)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Safe Lock Icon",
                        tint = JarvisGlowGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PRIVACY ASSURANCE SHIELD",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = JarvisGlowGreen
                    )
                }

                Text(
                    text = "Jarvis Lite does not bypass screen lock credentials, read passwords, or capture data in background windows. All automated gestures execute strictly on demand.",
                    fontSize = 12.sp,
                    color = JarvisTextWhite.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBlue),
        border = BorderStroke(1.dp, if (isGranted) JarvisTerminalGrey else JarvisNeonCyan.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextWhite,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = if (isGranted) "Granted" else "Pending",
                        tint = if (isGranted) JarvisGlowGreen else JarvisGlowOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGranted) "GRANTED" else "PENDING",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) JarvisGlowGreen else JarvisGlowOrange
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                color = JarvisTextMuted,
                lineHeight = 16.sp
            )

            if (!isGranted) {
                Button(
                    onClick = onConfigure,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("CONFIGURE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Command History Logging Screen Layout ---
@Composable
fun HistoryScreen(
    viewModel: JarvisViewModel
) {
    val historyLogs by viewModel.commandLogs.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HISTORICAL ACTIVITY LOGS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = JarvisTextMuted,
                letterSpacing = 1.5.sp
            )

            if (historyLogs.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearLogHistory() }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear all log history", tint = JarvisRiskRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (historyLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = "Empty History",
                        tint = JarvisTextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "NO HISTORIC LOGS FOUND",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trigger a command on the Home core dashboard, and the pipeline outcomes will log here.",
                        fontSize = 12.sp,
                        color = JarvisTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("command_logs_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyLogs) { log ->
                    CommandHistoryRow(log = log, sdf = sdf)
                }
            }
        }
    }
}

@Composable
fun CommandHistoryRow(
    log: CommandLog,
    sdf: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBlue),
        border = BorderStroke(1.dp, JarvisTerminalGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Success/failure indicator bubble
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (log.success) JarvisGlowGreen else JarvisRiskRed)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "USER: \"${log.rawText}\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextWhite
                    )
                    Text(
                        text = sdf.format(Date(log.timestamp)),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisTextMuted
                    )
                }

                Text(
                    text = "Parsed Intent: ${log.parsedIntent}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JarvisNeonCyan
                )

                Text(
                    text = "System Action: ${log.feedbackMessage}",
                    fontSize = 11.sp,
                    color = JarvisTextWhite.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// --- Local Settings Screen Layout ---
@Composable
fun SettingsScreen(
    viewModel: JarvisViewModel
) {
    var ttsEnabled by remember { mutableStateOf(viewModel.prefs.ttsEnabled) }
    var confirmationMode by remember { mutableStateOf(viewModel.prefs.confirmationMode) }
    var useLlmParser by remember { mutableStateOf(viewModel.prefs.useLlmParser) }
    var apiKey by remember { mutableStateOf(viewModel.prefs.geminiApiKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SYSTEM ENGINE SETTINGS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = JarvisTextMuted,
            letterSpacing = 1.5.sp
        )

        // TTS Setting Card
        ToggleCard(
            title = "Voice Speech Response (TTS)",
            subtitle = "Enable Jarvis vocal synthesizer replies when actions evaluate successfully or fail.",
            checked = ttsEnabled,
            onCheckedChange = {
                ttsEnabled = it
                viewModel.prefs.ttsEnabled = it
            }
        )

        // Confirmation Mode Card
        ToggleCard(
            title = "Safety Action Confirmation",
            subtitle = "Always prompt user approval before executing potentially risky actions (typing text, sending messaging links, or accessing chat groups).",
            checked = confirmationMode,
            onCheckedChange = {
                confirmationMode = it
                viewModel.prefs.confirmationMode = it
            }
        )

        // Pluggable Intent Parser Toggle Card
        ToggleCard(
            title = "Pluggable LLM Intent Parser (Gemini)",
            subtitle = "When disabled, Jarvis uses the fast 100% offline rule-based parser. Enable to route command extraction to Gemini Flash.",
            checked = useLlmParser,
            onCheckedChange = {
                useLlmParser = it
                viewModel.prefs.useLlmParser = it
            }
        )

        // Gemini API Key entry input
        AnimatedVisibility(
            visible = useLlmParser,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBlue),
                border = BorderStroke(1.dp, JarvisTerminalGrey)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "GEMINI SECRETS TOKEN",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = JarvisNeonCyan
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your Gemini API key...", color = JarvisTextMuted) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextWhite,
                            unfocusedTextColor = JarvisTextWhite,
                            focusedBorderColor = JarvisNeonCyan,
                            unfocusedBorderColor = JarvisTerminalGrey
                        ),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                viewModel.prefs.geminiApiKey = apiKey
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.prefs.geminiApiKey = apiKey }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                    contentDescription = "Save Key",
                                    tint = JarvisNeonCyan
                                )
                            }
                        }
                    )

                    Text(
                        text = "Note: Storing keys here is for prompt execution playground use only. Real keys should be managed via AI Studio config structures.",
                        fontSize = 11.sp,
                        color = JarvisTextMuted
                    )
                }
            }
        }

        // Limitations and Android sandbox policy disclaimer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisTerminalGrey.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, JarvisTerminalGrey.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ANDROID SANDBOX SECURE BOUNDARIES",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextMuted
                )

                Text(
                    text = "• Lock screens cannot be bypassed Programmatically without correct credential authorization.\n• Jarvis cannot perform clicks inside banking applications or system screens locked by SecureFlag.\n• Continuous background audio capture (Wake-words) requires system service foreground structures that heavily drain resources.",
                    fontSize = 12.sp,
                    color = JarvisTextMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBlue),
        border = BorderStroke(1.dp, JarvisTerminalGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JarvisTextWhite)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, fontSize = 12.sp, color = JarvisTextMuted, lineHeight = 16.sp)
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisDeepBlue,
                    checkedTrackColor = JarvisNeonCyan,
                    uncheckedThumbColor = JarvisTextMuted,
                    uncheckedTrackColor = JarvisTerminalGrey
                )
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRowWrapper(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(mainAxisSpacing),
        verticalArrangement = Arrangement.spacedBy(crossAxisSpacing)
    ) {
        content()
    }
}


