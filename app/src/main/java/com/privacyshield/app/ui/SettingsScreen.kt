package com.privacyshield.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.privacyshield.app.app
import com.privacyshield.app.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = ctx.app.settings
    val mode by s.themeMode.collectAsState()
    val black by s.pureBlack.collectAsState()
    val dynamic by s.dynamicColor.collectAsState()
    val seed by s.seedColor.collectAsState()
    var themeDialog by remember { mutableStateOf(false) }
    var colourDialog by remember { mutableStateOf(false) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    val wallpaperColours = dynamic && Build.VERSION.SDK_INT >= 31
    val modeName = when (mode) { Settings.MODE_LIGHT -> "Light"; Settings.MODE_DARK -> "Dark"; else -> "Follow system" }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Group("Appearance") {
                item(Icons.Outlined.DarkMode, "Theme", null, value = modeName) { themeDialog = true }
                item(Icons.Outlined.Palette, "Colour", if (wallpaperColours) "From your wallpaper" else colourName(seed),
                    trailing = { ColourDot(if (wallpaperColours) MaterialTheme.colorScheme.primary else Color(seed)); Chevron() }) { colourDialog = true }
                toggle(Icons.Outlined.Contrast, "Pure black dark theme", "True black backgrounds for AMOLED screens", black) { s.setPureBlack(it) }
            }
            Group("About") {
                item(Icons.Outlined.Info, "Privacy Shield", "Version $version", trailing = {}) {}
                item(Icons.Outlined.WifiOff, "No internet access", "The app can't send anything anywhere", trailing = {}) {}
            }
        }
    }

    if (themeDialog) ChoiceDialog(
        "Theme", listOf(Settings.MODE_SYSTEM to "Follow system", Settings.MODE_LIGHT to "Light", Settings.MODE_DARK to "Dark"),
        selected = mode, onDismiss = { themeDialog = false },
    ) { s.setThemeMode(it); themeDialog = false }

    if (colourDialog) AlertDialog(
        onDismissRequest = { colourDialog = false },
        title = { Text("Colour") },
        text = {
            ColourPicker(wallpaper = wallpaperColours, seed = seed, swatch = 40,
                onWallpaper = { s.setDynamicColor(true) }, onColour = { s.setSeedColor(it) })
        },
        confirmButton = { TextButton(onClick = { colourDialog = false }) { Text("Done") } },
    )
}
