package com.privacyshield.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Settings building blocks from DESIGN-GUIDE.md §3 (copied from Icon Mixer, plus a slider row).

/** Collects a section's rows so each can get its place-dependent rounded corners. */
class GroupScope {
    val rows = ArrayList<@Composable (RoundedCornerShape) -> Unit>()

    fun item(icon: ImageVector, title: String, hint: String?, value: String? = null, enabled: Boolean = true,
             trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
        rows += { shape ->
            SettingRow(icon, title, hint, shape, enabled, onClick) {
                when {
                    trailing != null -> trailing()
                    value != null -> {
                        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Chevron()
                    }
                    else -> Chevron()
                }
            }
        }
    }

    fun toggle(icon: ImageVector, title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        rows += { shape -> SettingRow(icon, title, hint, shape, true, { onChange(!checked) }) { Switch(checked, onChange) } }
    }

    /** A row with a label and a slider underneath (whole numbers from [range], [step] apart). */
    fun slider(icon: ImageVector, title: String, hint: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
        rows += { shape ->
            val cs = MaterialTheme.colorScheme
            Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(icon)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { onChange(((it / step).roundToInt() * step).coerceIn(range)) },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = (range.last - range.first) / step - 1,
                        modifier = Modifier.padding(start = 54.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun Group(title: String, build: GroupScope.() -> Unit) {
    val g = GroupScope().apply(build)
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val big = 22.dp; val small = 6.dp
        g.rows.forEachIndexed { i, row ->
            val first = i == 0; val last = i == g.rows.lastIndex
            row(RoundedCornerShape(
                topStart = if (first) big else small, topEnd = if (first) big else small,
                bottomStart = if (last) big else small, bottomEnd = if (last) big else small,
            ))
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).background(cs.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector, title: String, hint: String?, shape: RoundedCornerShape, enabled: Boolean, onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
                .alpha(if (enabled) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun ColourDot(c: Color) = Box(Modifier.padding(end = 4.dp).size(20.dp).background(c, CircleShape))

@Composable
fun ChoiceDialog(title: String, options: List<Pair<Int, String>>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = v == selected, onClick = { onPick(v) }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = v == selected, onClick = { onPick(v) })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
