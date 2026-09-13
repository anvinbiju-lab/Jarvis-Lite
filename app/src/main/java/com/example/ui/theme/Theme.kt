package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisNeonCyan,
    onPrimary = JarvisDeepBlue,
    primaryContainer = JarvisTerminalGrey,
    onPrimaryContainer = JarvisTextWhite,
    secondary = JarvisElectricBlue,
    onSecondary = JarvisTextWhite,
    background = JarvisDeepBlue,
    surface = JarvisCardBlue,
    onSurface = JarvisTextWhite,
    onBackground = JarvisTextWhite,
    error = JarvisRiskRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Theme for JARVIS aesthetic
    dynamicColor: Boolean = false, // Force custom JARVIS color palette instead of system dynamic colors
    content: @Composable () -> Unit,
) {
    val colorScheme = JarvisDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
