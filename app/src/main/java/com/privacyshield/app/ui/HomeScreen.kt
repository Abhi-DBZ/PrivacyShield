package com.privacyshield.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.privacyshield.app.app
import com.privacyshield.app.data.CrashLog
import com.privacyshield.app.data.Settings
import com.privacyshield.app.shield.AppWatcher
import com.privacyshield.app.shield.Reason
import com.privacyshield.app.shield.Shield
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSettings: () -> Unit, onApps: () -> Unit) {
    val ctx = LocalContext.current
    val s = ctx.app.settings
    val live by ctx.app.live.collectAsState()
    val problem by ctx.app.problem.collectAsState()
    val tilt by s.tilt.collectAsState()
    val tiltAngle by s.tiltAngle.collectAsState()
    val peek by s.peek.collectAsState()
    val lookAway by s.lookAway.collectAsState()
    val vibrate by s.vibrate.collectAsState()
    val style by s.style.collectAsState()
    val dim by s.dim.collectAsState()
    val onlyChosen by s.onlyChosen.collectAsState()
    val chosen by s.chosenApps.collectAsState()
    val reading by s.reading.collectAsState()
    val bandHeight by s.bandHeight.collectAsState()
    val landscapePause by s.landscapePause.collectAsState()
    val pictureVersion by s.pictureVersion.collectAsState()
    var styleDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Fake screen picture: the system photo picker needs no permission; we keep a private copy.
    val pictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (importPicture(ctx, uri, s.pictureFile)) { s.pictureChanged(); s.setStyle(Settings.STYLE_PICTURE) }
            else Toast.makeText(ctx, "Couldn't open that picture", Toast.LENGTH_SHORT).show()
        }
    }
    fun pickPicture() = pictureLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Permissions can change in system settings while we're away; re-check on every return.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose {} }
    val canDraw = remember(resumes) { Shield.canDraw(ctx) }
    val hasCamera = remember(resumes) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val detection = remember(resumes) { AppWatcher.granted(ctx) }

    fun openOverlaySettings() = ctx.startActivity(
        Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))

    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Shield.start(ctx) }
    fun turnOn() {
        when {
            !Shield.canDraw(ctx) -> openOverlaySettings()
            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> Shield.start(ctx)
        }
    }

    // Camera features ask for the camera first; the switch only turns on if it's allowed.
    var pendingCamera by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingCamera?.invoke()
        pendingCamera = null
        resumes++
    }
    fun withCamera(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) action()
        else { pendingCamera = action; cameraLauncher.launch(Manifest.permission.CAMERA) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Privacy Shield") },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            problem?.let { ProblemCard(it) }

            StatusCard(live.running, live, tilt, tiltAngle, peek || lookAway, onToggle = { if (live.running) Shield.stop(ctx) else turnOn() })

            if (!canDraw) {
                Spacer(Modifier.height(12.dp))
                Group("Setup") {
                    item(Icons.Outlined.Layers, "Allow display over other apps",
                        "Needed to hide the screen on top of whatever app is open", value = "Allow") { openOverlaySettings() }
                }
            }

            Group("Where") {
                toggle(Icons.Outlined.Apps, "Only in chosen apps",
                    "Banking, chats, gallery… Elsewhere the shield and camera rest, which saves battery", onlyChosen) { s.setOnlyChosen(it) }
                if (onlyChosen) {
                    item(Icons.Outlined.Checklist, "Chosen apps",
                        if (chosen.isEmpty()) "None yet: pick at least one" else "The shield works only while these are open",
                        value = "${chosen.size}") { onApps() }
                    if (!detection) {
                        item(Icons.Outlined.AccessibilityNew, "Allow usage access",
                            "Lets the shield see which app is open (only the app's name). Find Privacy Shield in the list " +
                                "and switch it on. Until then the shield works in every app.",
                            value = "Open") { openUsageAccess(ctx) }
                        item(Icons.Outlined.Lock, "Switch greyed out?",
                            "Some phones block this for apps not from the Play Store. Open App info → ⋮ (top right) → " +
                                "Allow restricted settings, then try again.",
                            value = "App info") {
                            ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        }
                    }
                }
            }

            Group("Protection") {
                toggle(Icons.Outlined.ScreenRotation, "Tilt guard",
                    "Hide the screen when the phone turns away from you (shown to a neighbour, laid flat)", tilt) { s.setTilt(it) }
                if (tilt) {
                    slider(Icons.Outlined.ScreenRotation, "Hide past $tiltAngle°",
                        "Lower hides sooner. Turns of a few degrees never count.", tiltAngle, 15..60, 5) { s.setTiltAngle(it) }
                    toggle(Icons.Outlined.StayCurrentLandscape, "Rest in landscape",
                        "Videos and games in landscape don't hide the screen. Back in portrait, your new hold counts.",
                        landscapePause) { s.setLandscapePause(it) }
                    item(Icons.Outlined.MyLocation, "Recenter",
                        if (live.running) "Use how you're holding the phone now" else "Turn the shield on first",
                        enabled = live.running) { Shield.send(ctx, Shield.ACTION_RECENTER) }
                }
                toggle(Icons.Outlined.Groups, "Peek alert",
                    "Front camera: hide when a second face looks at the screen", peek && hasCamera) { on ->
                    if (on) withCamera { s.setPeek(true) } else s.setPeek(false)
                }
                if (peek) toggle(Icons.Outlined.Vibration, "Vibrate on peek", "Two short buzzes when someone else is seen", vibrate) { s.setVibrate(it) }
                toggle(Icons.Outlined.VisibilityOff, "Hide when you look away",
                    "Front camera: hide after 3 seconds with no face in view", lookAway && hasCamera) { on ->
                    if (on) withCamera { s.setLookAway(true) } else s.setLookAway(false)
                }
            }

            Group("Reading window") {
                toggle(Icons.Outlined.ViewDay, "Reading window",
                    "Darkens everything except a strip you read through. Drag the ≡ handle on the right edge to move it.",
                    reading) { s.setReading(it) }
                if (reading) slider(Icons.Outlined.Height, "Strip height: $bandHeight%",
                    "Smaller shows less to people around you", bandHeight, 15..50, 5) { s.setBandHeight(it) }
            }

            Group("Look") {
                item(Icons.Outlined.BlurOn, "Hidden screen",
                    "What others see when it hides. Double-tap it to show for 30 s.",
                    value = styleName(style)) { styleDialog = true }
                if (style == Settings.STYLE_PICTURE) item(Icons.Outlined.Image, "Fake screen picture",
                    if (pictureVersion > 0 && s.pictureFile.exists()) "Tap to choose a different picture" else "Choose a picture",
                    value = "Change") { pickPicture() }
                slider(Icons.Outlined.Brightness6, if (dim == 0) "Dim filter: off" else "Dim filter: $dim%",
                    "Always-on dark layer. Harder to read from a distance, but darker for you too.", dim, 0..Settings.MAX_DIM, 5) { s.setDim(it) }
            }

            Group("Good to know") {
                item(Icons.Outlined.Info, "Not a hardware privacy screen",
                    "Phones with a privacy display steer the screen's light so side viewers get none. An app can't do " +
                        "that: it can only spot the risk (tilt, a second face) and hide the screen. It won't stop someone " +
                        "at your shoulder while the camera is off, or a photo taken of the screen.",
                    trailing = {}) {}
                item(Icons.Outlined.GppGood, "Camera stays on the phone",
                    "Faces are counted on the phone, ~3 frames a second, and nothing is saved. The app has no internet " +
                        "permission. Android shows its green camera dot while the camera features are on.",
                    trailing = {}) {}
            }
        }
    }

    if (styleDialog) ChoiceDialog(
        "Hidden screen",
        listOfNotNull(
            Settings.STYLE_BLACK to "Black (hides everything)",
            (Settings.STYLE_BLUR to "Blur (shapes and colours still show)").takeIf { Build.VERSION.SDK_INT >= 31 },
            Settings.STYLE_CLOCK to "Fake screen: clock",
            Settings.STYLE_PICTURE to "Fake screen: a picture you choose",
        ),
        selected = style, onDismiss = { styleDialog = false },
    ) {
        styleDialog = false
        if (it == Settings.STYLE_PICTURE && !s.pictureFile.exists()) pickPicture() else s.setStyle(it)
    }
}

@Composable
private fun StatusCard(
    running: Boolean, live: com.privacyshield.app.shield.Live, tilt: Boolean, tiltAngle: Int, camera: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).background(if (running) cs.primary else cs.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (running) Icons.Outlined.GppGood else Icons.Outlined.GppMaybe, null,
                        tint = if (running) cs.onPrimary else cs.onSurfaceVariant, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (running) "Shield is on" else "Shield is off", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !running -> "Your screen is not being watched over"
                            live.hiddenBy == Reason.TILT -> "Hidden now: phone turned away"
                            live.hiddenBy == Reason.PEEK -> "Hidden now: someone else is looking"
                            live.hiddenBy == Reason.AWAY -> "Hidden now: you looked away"
                            live.snoozedUntil > 0 -> "Showing for a few seconds"
                            !live.activeHere -> "Resting: works only in your chosen apps"
                            else -> "Watching. The screen hides by itself."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
            if (running && (tilt || camera)) {
                Spacer(Modifier.height(14.dp))
                Surface(color = cs.surfaceContainerHighest, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (tilt) Readout("Turned from your position", when {
                            live.tiltResting -> "resting (landscape)"
                            !live.activeHere -> "resting"
                            else -> live.tiltDeg?.let { "${it.toInt()}° (hides past $tiltAngle°)" } ?: "measuring…"
                        })
                        if (camera) Readout("Faces in view", live.faces?.toString() ?: "camera starting…")
                        live.cameraProblem?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (running) FilledTonalButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text("Turn off") }
            else Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text("Turn on") }
            if (!running) Text(
                "Hold the phone the way you normally do when turning it on: that becomes \"your position\". " +
                    "Also in Quick Settings: add the Privacy Shield tile.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Usage-access settings, opened straight on this app where the phone supports it. */
private fun openUsageAccess(ctx: android.content.Context) {
    val direct = Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    runCatching { ctx.startActivity(direct) }.onFailure {
        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }
}

private fun styleName(style: Int) = when (style) {
    Settings.STYLE_BLUR -> "Blur"
    Settings.STYLE_CLOCK -> "Clock"
    Settings.STYLE_PICTURE -> "Picture"
    else -> "Black"
}

/** Copies the picked image into app storage, scaled to at most ~2000 px and upright. */
private suspend fun importPicture(ctx: android.content.Context, uri: Uri, file: java.io.File): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > 2000) d.setTargetSampleSize(longest / 2000 + 1)
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
            }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)
    }

/** Last crash / start-up failure, with ways to get the text off the phone. */
@Composable
private fun ProblemCard(text: String) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), shape = RoundedCornerShape(28.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = cs.errorContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Something went wrong last time", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = cs.onErrorContainer)
            Text("Copy or share this report and send it to whoever is fixing the app.",
                style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer)
            Spacer(Modifier.height(10.dp))
            Surface(color = cs.surface.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text.lineSequence().take(14).joinToString("\n"), maxLines = 14,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Privacy Shield report", text))
                    android.widget.Toast.makeText(ctx, "Report copied", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                FilledTonalButton(onClick = {
                    ctx.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
                }) { Text("Share") }
                androidx.compose.material3.TextButton(onClick = { CrashLog.clear(ctx) }) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}
