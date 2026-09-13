package com.example.actions

sealed class JarvisAction {
    data class OpenApp(val appLabel: String, val packageName: String? = null) : JarvisAction()
    data class SearchMedia(val appLabel: String, val query: String) : JarvisAction()
    data class Scroll(val direction: ScrollDirection) : JarvisAction()
    data class TapIndexedItem(val index: Int, val description: String) : JarvisAction()
    data class GoToChats(val appLabel: String) : JarvisAction()
    data class OpenChatWithUser(val appLabel: String, val userName: String) : JarvisAction()
    data class TypeText(val text: String) : JarvisAction()
    data class SystemControl(val controlType: SystemControlType, val adjustment: AdjustmentType, val value: Int? = null) : JarvisAction()
    object GoHome : JarvisAction()
    object GoBack : JarvisAction()
    object LockScreen : JarvisAction()
    data class ClickText(val targetText: String) : JarvisAction()
    data class AnswerQuestion(val answer: String) : JarvisAction()
    
    // To-Do list control actions
    data class AddTodo(val title: String) : JarvisAction()
    object ListTodos : JarvisAction()
    data class CompleteTodo(val title: String) : JarvisAction()

    // File search action
    data class SearchFiles(val query: String) : JarvisAction()

    data class Unknown(val rawCommand: String) : JarvisAction()

    val isRisky: Boolean
        get() = this is TypeText || this is OpenChatWithUser || this is GoToChats
}

enum class ScrollDirection { UP, DOWN }
enum class SystemControlType { BRIGHTNESS, VOLUME, BLUETOOTH, WIFI }
enum class AdjustmentType { INCREASE, DECREASE, ENABLE, DISABLE }
