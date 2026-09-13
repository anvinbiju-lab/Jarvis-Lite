# 🤖 Jarvis Lite — Android Accessibility & AI Voice Assistant

**Jarvis Lite** is an advanced, native Android voice and accessibility automation assistant built with Kotlin and Jetpack Compose. Operating directly on-device, it merges on-device speech recognition, intelligent natural language parsing (Dual-engine: Local Rule-Based + Google Gemini 2.5 Flash), and the Android Accessibility Service framework to perform autonomous, hands-free UI interactions across your phone.

---

## ⚡ Key Capabilities & Features

### 1. 🧠 Dual-Engine Natural Language Parsing
* **Local Rule-Based Parser (Offline & Instant)**: Ultra-low latency regex and semantic matcher for immediate execution of system toggles, volume controls, app launches, and media searches.
* **Google Gemini 2.5 Flash AI Engine**: Understands complex, multi-intent natural language requests, extracting parameters into structured JSON action commands with automatic fallback.

### 2. 🦾 System & App Automation via Accessibility
* **Smart UI Interaction**: Programmatic node discovery to click buttons, tap by text/ID, scroll lists, focus input fields, and enter text hands-free.
* **Specialized App Automations**:
  * **YouTube**: Automated video search, selection, and playback.
  * **WhatsApp**: Automated contact search, chat opening, and message composition.
  * **Chrome & Web**: URL navigation and search execution.
  * **System Settings**: Direct shortcuts for Wi-Fi, Bluetooth, Sound, Display, and Battery.
* **System Actions**: Automated triggers for Home, Back, Recents, Notifications shade, Quick Settings, Lock Screen, and Screenshot capture.

### 3. 🎙️ Voice & Speech Pipeline
* **Real-Time Speech Recognition**: Real-time microphone capture with partial transcription streaming.
* **Text-to-Speech (TTS) Feedback**: Audible status confirmations and verbal execution summaries.
* **Visual Holographic Terminal**: Step-by-step pipeline logging (`Speech -> Intent -> Validation -> Execution -> Spoken Feedback`).

### 4. 🔮 Floating HUD Overlay & Quick Trigger
* **Draggable Floating Core (`JarvisForegroundService`)**: An overlay orb that stays accessible over any app.
* **Quick Action Popup (`JarvisPopupActivity`)**: Tap the floating core to trigger voice commands or type queries instantly without leaving your current app.

### 5. 🔁 Custom Macro Automation Engine
* Create, save, and execute multi-step automation routines.
* Chain actions with customizable delays (e.g., *Launch Music -> Wait 1.5s -> Set Volume to 80% -> Tap Play*).
* Local persistence powered by **Android Room (SQLite)**.

### 6. 🛡️ Safety & Permission Management
* **Interactive Permissions Center**: Real-time status monitors and deep-links for Accessibility Service, Overlay Window (`SYSTEM_ALERT_WINDOW`), Microphone (`RECORD_AUDIO`), and Notifications (`POST_NOTIFICATIONS`).
* **Execution Safeguards**: Prompts safety checks for sensitive actions (e.g., sending messages or modifying system configurations).

---

## 🏗️ Technical Architecture & Tech Stack

```
com.example
├── MainActivity.kt                  # Entry point & edge-to-edge Scaffold navigation
├── accessibility/
│   ├── JarvisAccessibilityService.kt # Accessibility node inspector & gesture dispatcher
│   └── JarvisAccessibilityManager.kt # Service lifecycle and connection binder
├── actions/
│   └── JarvisAction.kt              # Sealed action hierarchy & execution models
├── parser/
│   ├── CommandParser.kt             # Common parser contract
│   ├── RuleBasedParser.kt           # Offline fast regex intent parser
│   └── GeminiParser.kt              # Gemini 2.5 Flash AI structured intent parser
├── voice/
│   └── VoiceController.kt           # SpeechRecognizer & mic lifecycle manager
├── data/
│   ├── Database.kt                  # Room Entities, DAOs, and Database (Action Logs & Macros)
│   └── JarvisPreferences.kt         # User settings & preferences (SharedPreferences)
└── ui/
    ├── JarvisScreens.kt             # Jetpack Compose UI Screens (Core, Macros, Logs, Perms, Settings)
    ├── JarvisViewModel.kt           # Architecture ViewModel & StateFlow managers
    ├── popup/
    │   ├── JarvisForegroundService.kt # WindowManager floating HUD widget
    │   └── JarvisPopupActivity.kt   # Overlay dialog activity
    └── theme/                       # Sci-Fi Cyan & Obsidian Material 3 Theme
```

* **Language**: Kotlin (100%)
* **UI Toolkit**: Jetpack Compose with Material Design 3
* **Asynchronous Operations**: Kotlin Coroutines & `StateFlow`
* **Local Persistence**: Android Room Database (SQLite) & SharedPreferences
* **AI Model**: Google Gemini API (`gemini-2.5-flash`)
* **Target SDK**: Android 14 / 15 (API 34+)

---

## 🚀 Setup & Installation Guide

### Prerequisites
* Android device running **Android 8.0 (API 26)** or higher (Recommended: Android 12+).
* (Optional) Google Gemini API Key for AI Natural Language Parsing.

### Step-by-Step Configuration on Device
1. **Build & Install the APK**:
   * Export the project or generate the debug APK and install it on your device.
2. **Grant Required Permissions**:
   * Open **Jarvis Lite** and navigate to the **PERMS** tab.
   * **Accessibility Service**: Tap *Enable Accessibility* -> Find **Jarvis Lite** under *Installed Apps / Downloaded Apps* -> Turn it **ON**.
   * **Microphone Permission**: Allow audio recording for voice recognition.
   * **Floating Overlay**: Grant *Display over other apps* to enable the floating HUD orb.
   * **Notifications**: Enable notification permissions for foreground service persistence.
3. **Configure Gemini AI (Optional)**:
   * Go to the **SETTINGS** tab.
   * Enter your **Gemini API Key** and toggle **Use Gemini AI Parser** to enable advanced natural language understanding.

---

## 🔒 Privacy, Security & Sandbox Constraints

* **Zero Background Data Harvesting**: All screen inspection and accessibility tree processing occurs strictly in local memory and is discarded immediately after execution.
* **No Root Required**: Built strictly on standard public Android APIs and permissions.
* **Android Security Respect**:
  * Cannot inspect or interact with secure fields protected by `FLAG_SECURE` (e.g., banking apps, password fields).
  * Lock-screen interactions adhere to system security boundaries.

---

## 📤 Pushing to GitHub

To push this repository to GitHub directly from **Google AI Studio**:

1. Click on the **Project Settings / GitHub** button in the top navigation bar of Google AI Studio.
2. Select **Push to GitHub** (or **Export to GitHub Repository**).
3. Connect your GitHub account and choose your repository name (e.g., `jarvis-lite-android`).
4. Click **Publish / Push** — all source code, resources, Gradle configurations, and this `README.md` will be committed to your repository.

*Alternatively, if exporting as a ZIP file:*
```bash
git init
git add .
git commit -m "Initial commit: Jarvis Lite Android Assistant"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git push -u origin main
```

