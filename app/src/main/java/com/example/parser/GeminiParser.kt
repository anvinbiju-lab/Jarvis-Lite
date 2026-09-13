package com.example.parser

import android.util.Log
import com.example.actions.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiParser(
    private val apiKeyProvider: () -> String,
    private val fallbackParser: CommandParser = RuleBasedParser()
) : CommandParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun parse(command: String): JarvisAction = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            Log.d("GeminiParser", "No API key config. Falling back to rule-based parser.")
            return@withContext fallbackParser.parse(command)
        }

        val activePackage = com.example.accessibility.JarvisAccessibilityManager.activePackageName
        val screenText = com.example.accessibility.JarvisAccessibilityManager.getCurrentScreenText()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val prompt = parsePrompt(activePackage, screenText, command)

        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            // enforce response format to json if supported
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GeminiParser", "API call failed with response code ${response.code}. Falling back.")
                    return@withContext fallbackParser.parse(command)
                }

                val responseBody = response.body?.string() ?: ""
                if (responseBody.isEmpty()) {
                    return@withContext fallbackParser.parse(command)
                }

                val responseJson = JSONObject(responseBody)
                val textResponse = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                Log.d("GeminiParser", "Raw response text: $textResponse")

                // Map JSON response back to JarvisAction
                var rawJson = textResponse.trim()
                if (rawJson.startsWith("```")) {
                    rawJson = rawJson.replace(Regex("^```(?:json)?", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("```$"), "")
                        .trim()
                }
                val parsedObj = JSONObject(rawJson)
                val actionStr = parsedObj.optString("action", "Unknown")

                return@withContext when (actionStr) {
                    "OpenApp" -> JarvisAction.OpenApp(parsedObj.getString("appLabel"))
                    "SearchMedia" -> JarvisAction.SearchMedia(
                        parsedObj.optString("appLabel", "YouTube"),
                        parsedObj.getString("query")
                    )
                    "Scroll" -> {
                        val dir = if (parsedObj.optString("direction", "DOWN") == "UP") {
                            ScrollDirection.UP
                        } else {
                            ScrollDirection.DOWN
                        }
                        JarvisAction.Scroll(dir)
                    }
                    "TapIndexedItem" -> JarvisAction.TapIndexedItem(
                        parsedObj.optInt("index", 0),
                        parsedObj.optString("indexWord", "item")
                    )
                    "GoToChats" -> JarvisAction.GoToChats(parsedObj.optString("appLabel", "ActiveApp"))
                    "OpenChatWithUser" -> JarvisAction.OpenChatWithUser(
                        parsedObj.optString("appLabel", "ActiveApp"),
                        parsedObj.getString("userName")
                    )
                    "TypeText" -> JarvisAction.TypeText(parsedObj.getString("text"))
                    "SystemControl" -> {
                        val ctrlTypeStr = parsedObj.optString("controlType", "VOLUME")
                        val ctrl = when (ctrlTypeStr) {
                            "BRIGHTNESS" -> SystemControlType.BRIGHTNESS
                            "BLUETOOTH" -> SystemControlType.BLUETOOTH
                            "WIFI" -> SystemControlType.WIFI
                            else -> SystemControlType.VOLUME
                        }
                        val adjustmentStr = parsedObj.optString("adjustment", "INCREASE")
                        val adj = when (adjustmentStr) {
                            "DECREASE" -> AdjustmentType.DECREASE
                            "ENABLE" -> AdjustmentType.ENABLE
                            "DISABLE" -> AdjustmentType.DISABLE
                            else -> AdjustmentType.INCREASE
                        }
                        JarvisAction.SystemControl(ctrl, adj)
                    }
                    "GoHome" -> JarvisAction.GoHome
                    "GoBack" -> JarvisAction.GoBack
                    "LockScreen" -> JarvisAction.LockScreen
                    "ClickText" -> JarvisAction.ClickText(parsedObj.optString("targetText", ""))
                    "AddTodo" -> JarvisAction.AddTodo(parsedObj.getString("title"))
                    "ListTodos" -> JarvisAction.ListTodos
                    "CompleteTodo" -> JarvisAction.CompleteTodo(parsedObj.getString("title"))
                    "SearchFiles" -> JarvisAction.SearchFiles(parsedObj.getString("query"))
                    "AnswerQuestion" -> JarvisAction.AnswerQuestion(parsedObj.optString("answer", "I am a bit confused."))
                    "Unknown" -> JarvisAction.Unknown(command)
                    else -> fallbackParser.parse(command)
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiParser", "Error during Gemini parse: ${e.message}", e)
            return@withContext fallbackParser.parse(command)
        }
    }

    private fun parsePrompt(activePackage: String, screenText: String, command: String): String {
        return """
            You are "Jarvis" - a super powerful, friendly, and highly capable Android AI Companion like Siri or iron man's Jarvis.
            Convert the user's spoken command into a structured JSON object representing the user's intent. Highly leverage the current app screen context to answer questions or issue navigation.

            Current active app package on screen: "$activePackage"
            
            Current visible screen text content of active app:
            \"\"\"
            $screenText
            \"\"\"

            Spoken command: "$command"

            IMPORTANT Screen & App analysis directions:
            1. If you are in Instagram ("com.instagram.android") or WhatsApp ("com.whatsapp"), use that context!
               If the user says "go to the chat section", return "GoToChats" with appLabel "Instagram" or "WhatsApp".
               If they say "open direct message" or "go to direct messages", return "GoToChats".
               If they say "open chat with <name>" or similar, or they specify a chat name visible in the screen text above, return "OpenChatWithUser" with "userName" set to that name (or "ClickText" to tap that user's name on screen).
            2. If user requests opening, searching, or launching, map them to standard commands.
            3. If user wants general information, conversational advice, or to-do management, use the respective actions below.

            Supported intents (only return ONE valid JSON object):

            1. Action: "OpenApp"
               Fields: { "action": "OpenApp", "appLabel": "YouTube"|"Instagram"|"<app name>" }
            2. Action: "SearchMedia"
               Fields: { "action": "SearchMedia", "appLabel": "YouTube"|"Instagram"|"ActiveApp", "query": "Malayalam songs" }
            3. Action: "Scroll"
               Fields: { "action": "Scroll", "direction": "UP"|"DOWN" }
            4. Action: "TapIndexedItem"
               Fields: { "action": "TapIndexedItem", "index": 0|1|2|..., "indexWord": "first"|"second"|"third" }
            5. Action: "GoToChats"
               Fields: { "action": "GoToChats", "appLabel": "Instagram"|"WhatsApp"|"ActiveApp" }
            6. Action: "OpenChatWithUser"
               Fields: { "action": "OpenChatWithUser", "appLabel": "Instagram"|"WhatsApp"|"ActiveApp", "userName": "Anvin" }
            7. Action: "TypeText"
               Fields: { "action": "TypeText", "text": "hello, are you free?" }
            8. Action: "SystemControl"
               Fields: { "action": "SystemControl", "controlType": "VOLUME"|"BRIGHTNESS"|"BLUETOOTH"|"WIFI", "adjustment": "INCREASE"|"DECREASE"|"ENABLE"|"DISABLE" }
            9. Action: "GoHome"
               Fields: { "action": "GoHome" }
            10. Action: "GoBack"
                Fields: { "action": "GoBack" }
            11. Action: "LockScreen"
                Fields: { "action": "LockScreen" }
            12. Action: "ClickText"
                Fields: { "action": "ClickText", "targetText": "Search" }
            13. Action: "AddTodo" (Used when they want to store tasks, e.g. "drink water and add this to my today's to do list" -> title: "Drink water")
                Fields: { "action": "AddTodo", "title": "Drink water" }
            14. Action: "ListTodos" (Used when they query tasks, e.g. "What is today's to-do list?")
                Fields: { "action": "ListTodos" }
            15. Action: "CompleteTodo" (Used when they declare a task is done, e.g. "I done the Drink water task" or "remove drink water from my list" or "cancel check mail")
                Fields: { "action": "CompleteTodo", "title": "Drink water" }
            16. Action: "SearchFiles" (Used when they want to look up files, e.g. "find file invoice", "search file receipt")
                Fields: { "action": "SearchFiles", "query": "invoice" }
            17. Action: "AnswerQuestion" (Use this for general knowledge questions or conversational chat that does NOT control details on device. Return a helpful concise answer.)
                Fields: { "action": "AnswerQuestion", "answer": "A concise, helpful response to the user's question." }
            18. Action: "Unknown"
                Fields: { "action": "Unknown" }

            CRITICAL: Return ONLY valid, formatted JSON. No markdown backticks, no text explanations. If unsure, return action "Unknown".
        """.trimIndent()
    }
}
