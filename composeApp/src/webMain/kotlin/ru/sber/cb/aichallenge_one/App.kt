package ru.sber.cb.aichallenge_one

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import ru.sber.cb.aichallenge_one.ui.ChatScreen
import ru.sber.cb.aichallenge_one.ui.theme.GigaChatTheme

/**
 * Main application entry point with Material Design 3 theme.
 */
@Composable
fun App() {
    val systemDarkTheme = isSystemInDarkTheme()
    var isDarkTheme by remember { mutableStateOf(systemDarkTheme) }

    GigaChatTheme(darkTheme = isDarkTheme) {
        ChatScreen(
            isDarkTheme = isDarkTheme,
            onThemeToggle = { isDarkTheme = !isDarkTheme }
        )
    }
}
