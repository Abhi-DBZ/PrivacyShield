package com.privacyshield.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.privacyshield.app.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Pick the apps the shield works in (banking, chats, gallery …). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = ctx.app.settings
    val chosen by s.chosenApps.collectAsState()
    var query by remember { mutableStateOf("") }
    // Chosen apps go on top; the order is fixed when the screen opens so rows don't jump on tap.
    val pinned = remember { chosen }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val px = (40 * ctx.resources.displayMetrics.density).toInt()
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { ri ->
                    AppEntry(
                        ri.activityInfo.packageName,
                        ri.loadLabel(pm).toString(),
                        runCatching { ri.loadIcon(pm).toBitmap(px, px).asImageBitmap() }.getOrNull(),
                    )
                }
                .sortedWith(compareBy<AppEntry> { it.pkg !in pinned }.thenBy { it.label.lowercase() })
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Chosen apps (${chosen.size})") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val shown = if (query.isBlank()) list else list.filter { it.label.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.pkg }) { a ->
                        val on = a.pkg in chosen
                        Row(
                            Modifier.fillMaxWidth().clickable { s.setApp(a.pkg, !on) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (a.icon != null) Image(a.icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(a.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Checkbox(checked = on, onCheckedChange = { s.setApp(a.pkg, it) })
                        }
                    }
                }
            }
        }
    }
}
