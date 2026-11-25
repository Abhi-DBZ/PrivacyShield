package com.privacyshield.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.privacyshield.app.data.Settings

/** App colours offered in Settings (wallpaper colours come first, on Android 12+). */
val APP_COLOURS = listOf(
    "Indigo" to 0xFF3D4FC4, "Blue" to 0xFF1E88E5, "Teal" to 0xFF00897B, "Green" to 0xFF43A047,
    "Lime" to 0xFF7CB342, "Amber" to 0xFFFFB300, "Orange" to 0xFFF4511E, "Red" to 0xFFE53935,
    "Pink" to 0xFFD81B60, "Purple" to 0xFF8E24AA, "Brown" to 0xFF6D4C41, "Slate" to 0xFF546E7A,
).map { (n, c) -> n to c.toInt() }

private fun ColorScheme.pureBlack() = copy(
    background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D), surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF18181C), surfaceContainerHighest = Color(0xFF202025),
)

@Composable
fun PrivacyShieldTheme(settings: Settings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val mode by settings.themeMode.collectAsState()
    val black by settings.pureBlack.collectAsState()
    val dynamic by settings.dynamicColor.collectAsState()
    val seed by settings.seedColor.collectAsState()
    val dark = when (mode) {
        Settings.MODE_LIGHT -> false
        Settings.MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }
    var scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> com.materialkolor.dynamicColorScheme(seedColor = Color(seed), isDark = dark, isAmoled = false)
    }
    if (dark && black) scheme = scheme.pureBlack()
    MaterialTheme(colorScheme = scheme, content = content)
}

fun colourName(seed: Int) = APP_COLOURS.firstOrNull { it.second == seed }?.first ?: "Custom"

@Composable
fun ColourPicker(wallpaper: Boolean, seed: Int, swatch: Int = 40, onWallpaper: () -> Unit, onColour: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (Build.VERSION.SDK_INT >= 31) {
            Swatch(selected = wallpaper, fill = cs.surfaceContainerHighest, label = "Wallpaper colours", size = swatch, onClick = onWallpaper) {
                Icon(Icons.Outlined.Wallpaper, null, tint = if (wallpaper) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
        APP_COLOURS.forEach { (name, c) ->
            val on = !wallpaper && seed == c
            Swatch(selected = on, fill = Color(c), label = name, size = swatch, onClick = { onColour(c) }) {
                if (on) Icon(Icons.Outlined.Check, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun Swatch(selected: Boolean, fill: Color, label: String, size: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size.dp)
            .clip(CircleShape)
            .background(if (selected && fill == cs.surfaceContainerHighest) cs.primary else fill)
            .then(if (selected) Modifier.border(3.dp, cs.outline, CircleShape) else Modifier)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
