package com.privacyshield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.privacyshield.app.ui.AppsScreen
import com.privacyshield.app.ui.HomeScreen
import com.privacyshield.app.ui.PrivacyShieldTheme
import com.privacyshield.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PrivacyShieldTheme(app.settings) {
                var screen by rememberSaveable { mutableStateOf(HOME) }
                BackHandler(enabled = screen != HOME) { screen = HOME }
                when (screen) {
                    SETTINGS -> SettingsScreen(onBack = { screen = HOME })
                    APPS -> AppsScreen(onBack = { screen = HOME })
                    else -> HomeScreen(onSettings = { screen = SETTINGS }, onApps = { screen = APPS })
                }
            }
        }
    }

    private companion object {
        const val HOME = "home"
        const val SETTINGS = "settings"
        const val APPS = "apps"
    }
}
