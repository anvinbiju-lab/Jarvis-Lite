@file:Suppress("DEPRECATION")
package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.actions.*
import com.example.data.JarvisPreferences
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JarvisAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + job)
    
    private var tts: TextToSpeech? = null
    private lateinit var prefs: JarvisPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = JarvisPreferences(this)
        tts = TextToSpeech(this, this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("JarvisService", "Accessibility service connected")
        JarvisAccessibilityManager.onServiceConnected(this)
        speak("Jarvis is online and ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""
            if (packageName.isNotEmpty() && 
                packageName != "com.android.systemui" && 
                packageName != "android" &&
                !packageName.contains("jarvislite") && 
                !packageName.contains("com.example")) {
                
                JarvisAccessibilityManager.updateActivePackage(packageName)
            }
        }
    }

    override fun onInterrupt() {
        Log.e("JarvisService", "Accessibility service interrupted")
    }

    override fun onDestroy() {
        JarvisAccessibilityManager.onServiceDisconnected()
        tts?.shutdown()
        job.cancel()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    fun speak(text: String) {
        if (prefs.ttsEnabled) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
            } catch (e: Exception) {
                Log.e("JarvisService", "TTS speak failed", e)
            }
        }
    }

    suspend fun executeAccessibilityAction(action: JarvisAction): ActionResult {
        Log.d("JarvisService", "Service executing action: $action")
        
        return when (action) {
            is JarvisAction.GoHome -> {
                val success = performGlobalAction(GLOBAL_ACTION_HOME)
                if (success) {
                    speak("Going home.")
                    ActionResult(true, "Navigated homet.")
                } else {
                    ActionResult(false, "Failed to navigate home.")
                }
            }
            is JarvisAction.GoBack -> {
                val success = performGlobalAction(GLOBAL_ACTION_BACK)
                if (success) {
                    speak("Going back.")
                    ActionResult(true, "Navigated back.")
                } else {
                    ActionResult(false, "Failed to navigate back.")
                }
            }
            is JarvisAction.LockScreen -> {
                val success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                if (success) {
                    speak("Locking screen.")
                    ActionResult(true, "Screen locked.")
                } else {
                    // Try showing power options or closest system action
                    speak("Lock action restricted by Android API version. Opening power menu instead.")
                    val powerSuccess = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                    if (powerSuccess) {
                        ActionResult(true, "Opened power settings dialog.")
                    } else {
                        ActionResult(false, "Lock screen is not supported on this Android level.")
                    }
                }
            }
            is JarvisAction.OpenApp -> {
                openAppByLabel(action.appLabel)
            }
            is JarvisAction.SearchMedia -> {
                searchAppMedia(action.appLabel, action.query)
            }
            is JarvisAction.Scroll -> {
                performScroll(action.direction)
            }
            is JarvisAction.TapIndexedItem -> {
                tapIndexedItemOnScreen(action.index, action.description)
            }
            is JarvisAction.GoToChats -> {
                goToChatsDeepLink(action.appLabel)
            }
            is JarvisAction.OpenChatWithUser -> {
                openChatWithUserDeepLink(action.appLabel, action.userName)
            }
            is JarvisAction.TypeText -> {
                typeTextIntoActiveInput(action.text)
            }
            is JarvisAction.ClickText -> {
                clickTextOnScreen(action.targetText)
            }
            is JarvisAction.AnswerQuestion -> {
                speak(action.answer)
                ActionResult(true, action.answer)
            }
            is JarvisAction.SystemControl -> {
                ActionResult(false, "System control routing is handled via System APIs in UI thread.")
            }
            is JarvisAction.AddTodo -> {
                ActionResult(true, "ToDo addition routed on view-model thread.")
            }
            is JarvisAction.CompleteTodo -> {
                ActionResult(true, "ToDo completion routed on view-model thread.")
            }
            is JarvisAction.ListTodos -> {
                ActionResult(true, "ToDo list overview routed on view-model thread.")
            }
            is JarvisAction.SearchFiles -> {
                ActionResult(true, "File database search routed on view-model thread.")
            }
            is JarvisAction.Unknown -> {
                speak("I am sorry, I did not understand that command.")
                ActionResult(false, "Command unknown: '${action.rawCommand}'")
            }
        }
    }

    private fun openAppByLabel(appLabel: String): ActionResult {
        speak("Opening $appLabel.")
        val pm = packageManager
        val targetLabel = appLabel.trim().lowercase(Locale.getDefault())

        // Try Strategy 1: queryIntentActivities for launchers
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launchableApps = pm.queryIntentActivities(mainIntent, 0)
        
        // 1a. Exact Match on launchable apps
        for (info in launchableApps) {
            val label = info.loadLabel(pm).toString().trim().lowercase(Locale.getDefault())
            if (label == targetLabel) {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    return ActionResult(true, "App $appLabel opened successfully.")
                } catch (e: Exception) {
                    Log.e("JarvisService", "Failed to exact launch", e)
                }
            }
        }
        
        // 1b. Segment Match / Fuzzy match on launchable apps
        for (info in launchableApps) {
            val label = info.loadLabel(pm).toString().trim().lowercase(Locale.getDefault())
            if (label.contains(targetLabel) || targetLabel.contains(label)) {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    return ActionResult(true, "App $appLabel matched with $label and opened successfully.")
                } catch (e: Exception) {
                    Log.e("JarvisService", "Failed to fuzzy launch", e)
                }
            }
        }

        // Try Strategy 2: getInstalledApplications
        try {
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().trim().lowercase(Locale.getDefault())
                if (label == targetLabel || label.contains(targetLabel) || targetLabel.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        return ActionResult(true, "App $appLabel matched with installed app $label and opened successfully.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error reading installed applications", e)
        }

        // Strategy 3: Common hardcoded package mapping as fallback if package visibility fails entirely
        val fallbackPackage = when (targetLabel) {
            "youtube", "yt" -> "com.google.android.youtube"
            "instagram", "insta" -> "com.instagram.android"
            "whatsapp", "wa" -> "com.whatsapp"
            "facebook", "fb" -> "com.facebook.katana"
            "settings" -> "com.android.settings"
            else -> null
        }

        if (fallbackPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(fallbackPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return ActionResult(true, "App $appLabel launched via fallback package identifier.")
            } else {
                // Direct package fallback
                try {
                    val directIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage(fallbackPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(directIntent)
                    return ActionResult(true, "App $appLabel launched via direct package intent.")
                } catch (e: Exception) {
                    Log.e("JarvisService", "Direct package fallback failed", e)
                    // Last resort: Play Store
                    try {
                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$fallbackPackage")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(playIntent)
                        return ActionResult(false, "App '$appLabel' not found. Opened Play Store.")
                    } catch (e2: Exception) {
                        try {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$fallbackPackage")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(webIntent)
                            return ActionResult(false, "App '$appLabel' not found. Opened Play Store web.")
                        } catch (e3: Exception) {
                            // Ignored
                        }
                    }
                }
            }
        }

        speak("I could not find the app $appLabel on this phone.")
        return ActionResult(false, "App '$appLabel' not found on device.")
    }

    private fun searchAppMedia(appLabel: String, query: String): ActionResult {
        val targetAppLabel = if (appLabel == "ActiveApp") {
            when (JarvisAccessibilityManager.activePackageName) {
                "com.google.android.youtube" -> "YouTube"
                "com.instagram.android" -> "Instagram"
                else -> appLabel
            }
        } else {
            appLabel
        }
        
        speak("Searching $targetAppLabel for $query.")
        
        if (targetAppLabel.lowercase(Locale.getDefault()) == "youtube") {
            // Android System Media Search INTENT (100% reliable)
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
                return ActionResult(true, "YouTube search query '$query' sent via system intent.")
            } catch (e: Exception) {
                Log.e("JarvisService", "Failed YouTube media intent search, falling back to UI search", e)
            }
        } else if (targetAppLabel.lowercase(Locale.getDefault()) == "instagram") {
            // Try Instagram search intent or UI automation fallback
            // There's no direct "Search" intent for IG that doesn't just open the app.
            // But we can trigger a UI search by typing
            serviceScope.launch {
                // Assuming we might have a search button or we need to go to explore fragment
                // To keep it simple, we use clickTextOnScreen or typeText
                val searchSuccess = clickTextOnScreen("Search")
                if (searchSuccess.success) {
                    kotlinx.coroutines.delay(1000)
                    typeTextIntoActiveInput(query)
                } else {
                    speak("I am on Instagram, opening explore to search.")
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("instagram://explore")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("JarvisService", "IG explore deep link failed", e)
                    }
                }
            }
            return ActionResult(true, "Instagram search initiated for '$query'")
        }

        // Generic Web/Search Intent fallback
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", "$targetAppLabel $query")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(searchIntent)
            return ActionResult(true, "Sent system web search fallback for '$appLabel $query'.")
        } catch (e: Exception) {
            return ActionResult(false, "Could not perform search: ${e.localizedMessage}")
        }
    }

    private fun performScroll(direction: ScrollDirection): ActionResult {
        val rootNode = rootInActiveWindow ?: return ActionResult(false, "Active screen hierarchy is not accessible.")
        try {
            val scrollableNode = findScrollableNode(rootNode)
            if (scrollableNode != null) {
                val action = if (direction == ScrollDirection.DOWN) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val success = scrollableNode.performAction(action)
                try {
                    scrollableNode.recycle()
                } catch (ex: Exception) {}
                if (success) {
                    speak("Scrolling ${direction.name.lowercase(Locale.getDefault())}.")
                    return ActionResult(true, "Scrolled successfully.")
                } else {
                    return ActionResult(false, "Failed to perform scroll action on scroll container.")
                }
            } else {
                speak("No scrollable container was found on this screen.")
                return ActionResult(false, "No scrollable container found.")
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in performScroll", e)
            return ActionResult(false, "Scroll error: ${e.localizedMessage}")
        } finally {
            try {
                rootNode.recycle()
            } catch (ex: Exception) {}
        }
    }

    private fun tapIndexedItemOnScreen(index: Int, indexWord: String): ActionResult {
        val rootNode = rootInActiveWindow ?: return ActionResult(false, "Active screen is not readable.")
        val clickables = mutableListOf<AccessibilityNodeInfo>()
        try {
            findAllClickableNodes(rootNode, clickables)

            if (index >= 0 && index < clickables.size) {
                val node = clickables[index]
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    speak("Tapping the $indexWord item.")
                    return ActionResult(true, "Clicked clickable item index $index on screen.")
                } else {
                    return ActionResult(false, "Failed to perform click on item index $index.")
                }
            } else {
                speak("I could not find the $indexWord clickable item on this screen.")
                return ActionResult(false, "Clickable index $index out of bounds (found ${clickables.size} clickables).")
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in tapIndexedItemOnScreen", e)
            return ActionResult(false, "Tap error: ${e.localizedMessage}")
        } finally {
            clickables.forEach {
                try {
                    it.recycle()
                } catch (ex: Exception) {}
            }
            try {
                rootNode.recycle()
            } catch (ex: Exception) {}
        }
    }

    private fun clickTextOnScreen(targetText: String): ActionResult {
        val rootNode = rootInActiveWindow ?: return ActionResult(false, "Active screen is inaccessible.")
        var matchingNode: AccessibilityNodeInfo? = null
        var clickableNode: AccessibilityNodeInfo? = null
        try {
            matchingNode = findNodeByText(rootNode, targetText)

            if (matchingNode != null) {
                clickableNode = findClickableAncestor(matchingNode)
                val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    speak("Clicking $targetText.")
                    return ActionResult(true, "Successfully clicked target text '$targetText'.")
                } else {
                    return ActionResult(false, "Found text '$targetText' but click action failed.")
                }
            } else {
                speak("I was unable to find $targetText on screen.")
                return ActionResult(false, "Text '$targetText' not found in active screen window.")
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in clickTextOnScreen", e)
            return ActionResult(false, "Click text error: ${e.localizedMessage}")
        } finally {
            try {
                matchingNode?.recycle()
            } catch (ex: Exception) {}
            try {
                clickableNode?.recycle()
            } catch (ex: Exception) {}
            try {
                rootNode.recycle()
            } catch (ex: Exception) {}
        }
    }

    private fun goToChatsDeepLink(appLabel: String): ActionResult {
        speak("Opening chats on $appLabel.")
        val isActiveIsInstagram = appLabel == "ActiveApp" && JarvisAccessibilityManager.activePackageName == "com.instagram.android"
        if (appLabel.lowercase(Locale.getDefault()) == "instagram" || isActiveIsInstagram) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://direct_inbox")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
                return ActionResult(true, "Deep link 'instagram://direct_inbox' opened successfully.")
            } catch (e: Exception) {
                Log.e("JarvisService", "Instagram direct deep link failed", e)
            }
        }
        
        val isActiveIsWhatsApp = appLabel == "ActiveApp" && JarvisAccessibilityManager.activePackageName == "com.whatsapp"
        // WhatsApp chats fallback
        if (appLabel.lowercase(Locale.getDefault()) == "whatsapp" || isActiveIsWhatsApp) {
            val pm = packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return ActionResult(true, "WhatsApp app opened as chats fallback.")
            }
        }

        // Fallback to searching chats on screen
        return clickTextOnScreen("Chats")
    }

    private fun openChatWithUserDeepLink(appLabel: String, userName: String): ActionResult {
        speak("Opening chat with $userName.")
        val isActiveIsInstagram = appLabel == "ActiveApp" && JarvisAccessibilityManager.activePackageName == "com.instagram.android"
        if (appLabel.lowercase(Locale.getDefault()) == "instagram" || isActiveIsInstagram) {
            // Instagram profile/direct deep link standard prefix
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://direct_inbox")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
                // We open dm box first, then click on user text or search
                serviceScope.launch {
                    kotlinx.coroutines.delay(1500)
                    clickTextOnScreen(userName)
                }
                return ActionResult(true, "Instagram chats opened; waiting to select chat with $userName.")
            } catch (e: Exception) {
                Log.e("JarvisService", "Deep link failed", e)
            }
        }

        val isActiveIsWhatsApp = appLabel == "ActiveApp" && JarvisAccessibilityManager.activePackageName == "com.whatsapp"
        // WhatsApp Chat Link (with query parameter contact name starts message compose)
        if (appLabel.lowercase(Locale.getDefault()) == "whatsapp" || isActiveIsWhatsApp) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:")).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
                serviceScope.launch {
                    kotlinx.coroutines.delay(1200)
                    clickTextOnScreen(userName)
                }
                return ActionResult(true, "WhatsApp opened contacts; searching for '$userName'.")
            } catch (e: Exception) {
                Log.e("JarvisService", "WhatsApp messaging link failed", e)
            }
        }

        return clickTextOnScreen(userName)
    }

    private fun typeTextIntoActiveInput(text: String): ActionResult {
        val rootNode = rootInActiveWindow ?: return ActionResult(false, "Active screen hierarchy is unavailable.")
        var focusedInput: AccessibilityNodeInfo? = null
        try {
            focusedInput = findEditableNode(rootNode)

            if (focusedInput != null) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val success = focusedInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (success) {
                    speak("Typed: $text")
                    return ActionResult(true, "Text '$text' typed into editable field.")
                } else {
                    return ActionResult(false, "Failed to inject letters into input field.")
                }
            } else {
                speak("There is no active typing box focused on this screen.")
                return ActionResult(false, "No active focused editable field found on screen.")
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in typeTextIntoActiveInput", e)
            return ActionResult(false, "Type text error: ${e.localizedMessage}")
        } finally {
            try {
                focusedInput?.recycle()
            } catch (ex: Exception) {}
            try {
                rootNode.recycle()
            } catch (ex: Exception) {}
        }
    }

    // --- Node Navigation Utilities ---

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            if (node.isScrollable) {
                return AccessibilityNodeInfo.obtain(node)
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                } ?: continue
                val scrollable = findScrollableNode(child)
                try {
                    child.recycle()
                } catch (e: Exception) {}
                if (scrollable != null) {
                    return scrollable
                }
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in findScrollableNode", e)
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) {
                return AccessibilityNodeInfo.obtain(node)
            }
            if (node.isFocused && (node.className?.contains("EditText", ignoreCase = true) == true)) {
                return AccessibilityNodeInfo.obtain(node)
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                } ?: continue
                val found = findEditableNode(child)
                try {
                    child.recycle()
                } catch (e: Exception) {}
                if (found != null) {
                    return found
                }
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in findEditableNode", e)
        }
        return null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        try {
            val nodeText = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            if (nodeText.contains(text, ignoreCase = true) || contentDesc.contains(text, ignoreCase = true)) {
                return AccessibilityNodeInfo.obtain(node)
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                } ?: continue
                val found = findNodeByText(child, text)
                try {
                    child.recycle()
                } catch (e: Exception) {}
                if (found != null) {
                    return found
                }
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in findNodeByText", e)
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        try {
            var current = AccessibilityNodeInfo.obtain(node)
            while (true) {
                if (current.isClickable) {
                    return current
                }
                val parentNode = try {
                    current.parent
                } catch (e: Exception) {
                    null
                }
                if (parentNode == null) {
                    break
                }
                try {
                    current.recycle()
                } catch (e: Exception) {}
                current = parentNode
            }
            try {
                current.recycle()
            } catch (e: Exception) {}
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in findClickableAncestor", e)
        }
        return AccessibilityNodeInfo.obtain(node)
    }

    private fun findAllClickableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        try {
            if (node.isClickable && node.isVisibleToUser) {
                list.add(AccessibilityNodeInfo.obtain(node))
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                } ?: continue
                findAllClickableNodes(child, list)
                try {
                    child.recycle()
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in findAllClickableNodes", e)
        }
    }

    private fun traverseAndCollectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 50) return
        try {
            if (node.isVisibleToUser) {
                val text = node.text?.toString()
                val desc = node.contentDescription?.toString()
                if (!text.isNullOrBlank()) {
                    sb.append(text).append("\n")
                } else if (!desc.isNullOrBlank()) {
                    sb.append(desc).append("\n")
                }
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                } ?: continue
                traverseAndCollectText(child, sb, depth + 1)
                try {
                    child.recycle()
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error in traverseAndCollectText", e)
        }
    }

    suspend fun captureCurrentScreenText(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        var text = ""
        var root: AccessibilityNodeInfo? = null
        try {
            root = rootInActiveWindow
            if (root != null) {
                val sb = StringBuilder()
                traverseAndCollectText(root, sb, 0)
                text = sb.toString().trim()
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Error capturing screen text on demand", e)
        } finally {
            try {
                root?.recycle()
            } catch (ex: Exception) {}
        }
        text
    }
}
