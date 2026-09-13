package com.example.ui.popup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisDeepBlue
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.JarvisViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.accessibility.JarvisAccessibilityManager
import com.example.ui.JarvisCoreCircle

class JarvisPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Reset status so we don't immediately close if the last state was Executed/Failed
        if (JarvisAccessibilityManager.isServiceConnected.value) {
            JarvisAccessibilityManager.updateStatus("Connected & Ready")
        } else {
            JarvisAccessibilityManager.updateStatus("Ready")
        }
        
        val greetings = listOf(
            "Hey Anvin, what's up?",
            "How can I help you?",
            "Jarvis online. What do you need?",
            "Yes, sir?",
            "At your service.",
            "What's on your mind?"
        )
        JarvisAccessibilityManager.speak(greetings.random())

        setContent {
            com.example.ui.theme.MyApplicationTheme {
                val viewModel: JarvisViewModel = viewModel()
                
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                            android.util.Log.d("JarvisPopupActivity", "Activity stopped; pausing microphone listening")
                            viewModel.voiceController.stopListening()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                
                // Start listening automatically when popup opens
                LaunchedEffect(Unit) {
                    if (!viewModel.voiceController.isListening.value) {
                        viewModel.triggerMic()
                    }
                }

                val pipelineStatus by viewModel.activePipelineStatus.collectAsState()

                LaunchedEffect(pipelineStatus) {
                    // Finish when execution starts so the popup doesn't block the screen while Jarvis takes action
                    if (pipelineStatus.contains("Executing action...")) {
                        finish()
                    } else if (pipelineStatus.contains("Executed successfully") || 
                               pipelineStatus.contains("Failed:")) {
                        // Add a delay to show the final text, then yield focus
                        kotlinx.coroutines.delay(1000)
                        finish()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                            .clickable(enabled = false) {}, // Prevent taps on the card from propagating to the background bounds
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = JarvisDeepBlue)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Hey Anvin, what's up!",
                                color = JarvisNeonCyan,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            val isListening by viewModel.voiceController.isListening.collectAsState()
                            val text by viewModel.currentCommandText.collectAsState()
                            val error by viewModel.voiceController.error.collectAsState()
                            
                            Box(modifier = Modifier.clickable { viewModel.triggerMic() }) {
                                JarvisCoreCircle(isListening, modifier = Modifier.size(120.dp))
                            }
                            
                            if (text.isNotEmpty()) {
                                Text(
                                    text = text,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 16.dp),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                            
                            if (error != null) {
                                Text(
                                    text = error!!,
                                    color = Color.Red,
                                    modifier = Modifier.padding(top = 8.dp),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }

                            if (pipelineStatus != "Connected & Ready" && pipelineStatus != "Disconnected") {
                                Text(
                                    text = pipelineStatus,
                                    color = JarvisNeonCyan.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 8.dp),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
