package com.example.accessibility

import com.example.actions.JarvisAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object JarvisAccessibilityManager {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected

    private val _currentStatus = MutableStateFlow("Inactive")
    val currentStatus: StateFlow<String> = _currentStatus

    private var activeService: JarvisAccessibilityService? = null
    
    var activePackageName: String = "com.android.launcher"
        private set

    fun updateActivePackage(packageName: String) {
        if (packageName.isNotEmpty() && 
            packageName != "com.android.systemui" && 
            packageName != "android" &&
            packageName != "com.android.launcher" &&
            packageName != "com.android.launcher3" &&
            !packageName.contains("jarvislite") && 
            !packageName.contains("com.example")) {
            activePackageName = packageName
        }
    }

    suspend fun getCurrentScreenText(): String {
        return activeService?.captureCurrentScreenText() ?: ""
    }

    fun onServiceConnected(service: JarvisAccessibilityService) {
        activeService = service
        _isServiceConnected.value = true
        _currentStatus.value = "Connected & Ready"
    }

    fun onServiceDisconnected() {
        activeService = null
        _isServiceConnected.value = false
        _currentStatus.value = "Disconnected"
    }

    fun updateStatus(status: String) {
        _currentStatus.value = status
    }

    suspend fun executeAction(action: JarvisAction): ActionResult {
        val service = activeService
        if (service == null) {
            _currentStatus.value = "Failed: Service is offline"
            return ActionResult(false, "Accessibility Service is not enabled.")
        }
        _currentStatus.value = "Executing action..."
        val result = service.executeAccessibilityAction(action)
        if (result.success) {
            _currentStatus.value = "Executed successfully"
        } else {
            _currentStatus.value = "Failed: ${result.message}"
        }
        return result
    }

    fun speak(text: String) {
        activeService?.speak(text)
    }
}

data class ActionResult(val success: Boolean, val message: String)
