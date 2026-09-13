package com.example.parser

import com.example.actions.*
import java.util.Locale

class RuleBasedParser : CommandParser {
    override suspend fun parse(command: String): JarvisAction {
        var clean = command.trim().lowercase(Locale.getDefault())
        
        // Remove common conversational fillers
        val prefixRegex = Regex("""^(can you|could you|would you|please|just)\s+""")
        val suffixRegex = Regex("""\s+(for me|please|now)$""")
        clean = clean.replace(prefixRegex, "").trim()
        clean = clean.replace(suffixRegex, "").trim()

        if (clean.isEmpty()) return JarvisAction.Unknown(command)

        // 1. Navigation Basics
        if (clean == "go home" || clean == "navigate home" || clean == "go back to home" || clean == "home") {
            return JarvisAction.GoHome
        }
        if (clean == "go back" || clean == "navigate back" || clean == "back") {
            return JarvisAction.GoBack
        }
        if (clean == "lock screen" || clean == "lock the phone" || clean == "lock phone") {
            return JarvisAction.LockScreen
        }

        // 2. Open apps
        val openAppRegex = Regex("""^(?:open|launch|go to)\s+(?:the\s+)?([a-z0-9\s]+)$""")
        openAppRegex.find(clean)?.let { match ->
            val app = match.groupValues[1].trim()
            if (app != "chats" && app != "chat") {
                return JarvisAction.OpenApp(capitalizeWords(app))
            }
        }

        val searchRegex3 = Regex("""^(?:open|launch)\s+(?:the\s+)?([a-z0-9\s]+)\s+and\s+search\s+(?:for\s+)?([a-z0-9\s,\'\"\?\!\.\_]+)$""")
        searchRegex3.find(clean)?.let { match ->
            val app = match.groupValues[1].trim()
            val query = match.groupValues[2].trim()
            return JarvisAction.SearchMedia(capitalizeWords(app), query)
        }

        // 3. Search App/Media (e.g. "search youtube for Malayalam songs")
        val searchRegex1 = Regex("""^search\s+([a-z0-9\s]+)\s+for\s+([a-z0-9\s,\'\"\?\!\.\_]+)$""")
        searchRegex1.find(clean)?.let { match ->
            val app = match.groupValues[1].trim()
            val query = match.groupValues[2].trim()
            return JarvisAction.SearchMedia(capitalizeWords(app), query)
        }
        val searchRegex2 = Regex("""^on\s+([a-z0-9\s]+)\s+search\s+for\s+([a-z0-9\s,\'\"\?\!\.\_]+)$""")
        searchRegex2.find(clean)?.let { match ->
            val app = match.groupValues[1].trim()
            val query = match.groupValues[2].trim()
            return JarvisAction.SearchMedia(capitalizeWords(app), query)
        }

        // Implicit search: "search for tamil movies" -> ActiveApp
        val searchRegexImplicit = Regex("""^search\s+(?:for\s+)?([a-z0-9\s,\'\"\?\!\.\_]+)$""")
        searchRegexImplicit.find(clean)?.let { match ->
            val query = match.groupValues[1].trim()
            return JarvisAction.SearchMedia("ActiveApp", query)
        }

        // 4. Scroll Control
        if (clean.contains("scroll down") || clean.contains("swipe down")) {
            return JarvisAction.Scroll(ScrollDirection.DOWN)
        }
        if (clean.contains("scroll up") || clean.contains("swipe up")) {
            return JarvisAction.Scroll(ScrollDirection.UP)
        }

        // 5. Open/Go to Chats
        if (clean == "go to chats" || clean == "open chats" || clean == "open direct messages" || clean == "go to direct messages") {
            return JarvisAction.GoToChats("ActiveApp")
        }

        // 6. Open chat with user
        val chatUserRegex = Regex("""^(?:open|start)\s+(?:the\s+)?chat\s+with\s+([a-z0-9\s]+)$""")
        chatUserRegex.find(clean)?.let { match ->
            val user = match.groupValues[1].trim()
            return JarvisAction.OpenChatWithUser("ActiveApp", capitalizeWords(user))
        }

        // 7. Click indexed item (e.g. "tap the third video", "click the first item")
        val indexRegex = Regex("""^(?:tap|click|open|select)\s+(?:the\s+)?(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)\s+([a-z0-9\s]+)?$""")
        indexRegex.find(clean)?.let { match ->
            val indexWord = match.groupValues[1]
            val desc = match.groupValues[2].ifEmpty { "item" }
            val index = when (indexWord) {
                "first" -> 0
                "second" -> 1
                "third" -> 2
                "fourth" -> 3
                "fifth" -> 4
                "sixth" -> 5
                "seventh" -> 6
                "eighth" -> 7
                "ninth" -> 8
                "tenth" -> 9
                else -> 0
            }
            return JarvisAction.TapIndexedItem(index, indexWord)
        }

        // 8. Type Text (e.g. "type hello, are you free?")
        val typeRegex = Regex("""^(?:type|input|write|send text)\s+(.+)$""")
        typeRegex.find(clean)?.let { match ->
            val text = match.groupValues[1].trim()
            return JarvisAction.TypeText(text)
        }

        // 9. System Control - Volume
        if (clean.contains("increase volume") || clean.contains("volume up") || clean.contains("louder") || clean == "make it louder") {
            return JarvisAction.SystemControl(SystemControlType.VOLUME, AdjustmentType.INCREASE)
        }
        if (clean.contains("reduce volume") || clean.contains("decrease volume") || clean.contains("volume down") || clean == "make it quieter") {
            return JarvisAction.SystemControl(SystemControlType.VOLUME, AdjustmentType.DECREASE)
        }

        // 10. System Control - Brightness
        if (clean.contains("increase brightness") || clean.contains("brightness up") || clean.contains("make it brighter") || clean == "brighter") {
            return JarvisAction.SystemControl(SystemControlType.BRIGHTNESS, AdjustmentType.INCREASE)
        }
        if (clean.contains("reduce brightness") || clean.contains("decrease brightness") || clean.contains("brightness down") || clean.contains("dim screen") || clean == "dimmer") {
            return JarvisAction.SystemControl(SystemControlType.BRIGHTNESS, AdjustmentType.DECREASE)
        }

        // 11. System Control - Bluetooth Control
        if (clean.contains("turn on bluetooth") || clean.contains("enable bluetooth") || clean.contains("bluetooth on") || clean == "bluetooth") {
            return JarvisAction.SystemControl(SystemControlType.BLUETOOTH, AdjustmentType.ENABLE)
        }
        if (clean.contains("turn off bluetooth") || clean.contains("disable bluetooth") || clean.contains("bluetooth off")) {
            return JarvisAction.SystemControl(SystemControlType.BLUETOOTH, AdjustmentType.DISABLE)
        }

        // 12. System Control - WiFi Control
        if (clean.contains("turn on wifi") || clean.contains("enable wifi") || clean.contains("wifi on") || clean == "wifi") {
            return JarvisAction.SystemControl(SystemControlType.WIFI, AdjustmentType.ENABLE)
        }
        if (clean.contains("turn off wifi") || clean.contains("disable wifi") || clean.contains("wifi off")) {
            return JarvisAction.SystemControl(SystemControlType.WIFI, AdjustmentType.DISABLE)
        }

        // 13. File Search Control
        val fileSearchRegex1 = Regex("""^(?:find|search|look for)(?:\s+a)?\s+file\s+(?:named|name)?\s+(.+)$""")
        fileSearchRegex1.find(clean)?.let { match ->
            return JarvisAction.SearchFiles(match.groupValues[1].trim())
        }
        val fileSearchRegex2 = Regex("""^(?:find|search)\s+(.+)\s+in\s+(?:the\s+)?file\s+manager$""")
        fileSearchRegex2.find(clean)?.let { match ->
            return JarvisAction.SearchFiles(match.groupValues[1].trim())
        }
        val fileSearchRegex3 = Regex("""^search\s+for\s+(.+)\s+in\s+files$""")
        fileSearchRegex3.find(clean)?.let { match ->
            return JarvisAction.SearchFiles(match.groupValues[1].trim())
        }

        // 14. To-Do List Controls
        if (clean == "what is today's to-do list" || clean == "what is on my to-do list" || 
            clean.contains("read my to do list") || clean.contains("tell me my to do list") || 
            clean.contains("what is my to do list") || clean == "to-do list" || clean == "what's today's to-do list") {
            return JarvisAction.ListTodos
        }

        val addTodoRegex1 = Regex("""^add\s+(.+?)\s+to\s+(?:my\s+)?(?:today\'s\s+)?(?:to-?do\s+)?list$""")
        addTodoRegex1.find(clean)?.let { match ->
            return JarvisAction.AddTodo(match.groupValues[1].trim())
        }
        // supports: "drink water and say add this to my today's to do list"
        val addTodoRegex2 = Regex("""^(.+?)\s+and\s+(?:then\s+)?(?:say\s+)?add\s+(?:this|it)\s+to\s+(?:my\s+)?(?:today\'s\s+)?(?:to-?do\s+)?list$""")
        addTodoRegex2.find(clean)?.let { match ->
            return JarvisAction.AddTodo(match.groupValues[1].trim())
        }
        val addTodoRegex3 = Regex("""^add\s+(?:this|it)\s+to\s+(?:my\s+)?to-?do\s+list\s*:\s*(.+)$""")
        addTodoRegex3.find(clean)?.let { match ->
            return JarvisAction.AddTodo(match.groupValues[1].trim())
        }
        if (clean.contains("drink water") && (clean.contains("add this") || clean.contains("add to my todolist"))) {
            return JarvisAction.AddTodo("Drink water")
        }

        val completeTodoRegex1 = Regex("""^(?:i\s+)?(?:done|completed|finished)\s+(?:the\s+)?(.+?)(?:\s+task|\s+do)?$""")
        completeTodoRegex1.find(clean)?.let { match ->
            return JarvisAction.CompleteTodo(match.groupValues[1].trim())
        }
        val completeTodoRegex2 = Regex("""^(?:cancel|remove|delete)\s+(.+?)\s+from\s+(?:my\s+)?(?:to-?do\s+)?list$""")
        completeTodoRegex2.find(clean)?.let { match ->
            return JarvisAction.CompleteTodo(match.groupValues[1].trim())
        }
        val completeTodoRegex3 = Regex("""^(?:mark|set)\s+(.+?)\s+as\s+(?:done|completed)$""")
        completeTodoRegex3.find(clean)?.let { match ->
            return JarvisAction.CompleteTodo(match.groupValues[1].trim())
        }

        // 15. Custom tap actions (e.g. "click search", "tap checkout")
        val tapRegex = Regex("""^(?:tap|click|press)\s+([a-z0-9\s]+)$""")
        tapRegex.find(clean)?.let { match ->
            val target = match.groupValues[1].trim()
            return JarvisAction.ClickText(capitalizeWords(target))
        }

        // 12. Context-aware fallback: If short phrase on media apps, assume search.
        val currentPackage = com.example.accessibility.JarvisAccessibilityManager.activePackageName
        if (clean.split(" ").size <= 5) {
            if (currentPackage == "com.google.android.youtube" || currentPackage == "com.instagram.android") {
                return JarvisAction.SearchMedia("ActiveApp", command)
            }
        }

        return JarvisAction.Unknown(command)
    }

    private fun capitalizeWords(input: String): String {
        return input.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
