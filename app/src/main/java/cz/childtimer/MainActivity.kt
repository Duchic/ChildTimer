package cz.childtimer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Paper = Color(0xFFFFF9F2)
private val Ink = Color(0xFF10182D)
private val Muted = Color(0xFF555C6D)
private val Track = Color(0xFFE0E1E5)
private val Palette = listOf(Color(0xFF008D80), Color(0xFF0866D9), Color(0xFF7743C9), Color(0xFFB84C08), Color(0xFFC02B73))
private val ColorNames = listOf("Tyrkysová", "Modrá", "Fialová", "Oranžová", "Růžová")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChildTimerApp(this) }
    }
}

@Composable
private fun TimerTheme(color: Int, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Palette[color], onPrimary = Color.White,
        background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
        onSurfaceVariant = Muted, surfaceVariant = Color(0xFFF0EBE5),
    ), content = content)
}

@Composable
private fun ChildTimerApp(activity: ComponentActivity) {
    val context = LocalContext.current
    val store = remember { TimerStore(context) }
    val alarm = remember { TimerAlarm(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var settings by remember { mutableStateOf(store.preferences()) }
    var timer by remember { mutableStateOf(store.countdown()) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var selectedMinutes by rememberSaveable { mutableIntStateOf(15) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var editMinutes by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var needsExact by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun update(next: Countdown) {
        store.save(next)
        timer = next
        alarm.schedule(next)
    }
    fun start(minutes: Int) {
        if (!alarm.canScheduleExactly()) { needsExact = true; return }
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        alarm.dismiss()
        locked = false
        now = SystemClock.elapsedRealtime()
        update(Countdown.start(minutes, now))
    }
    fun reset() {
        update(Countdown())
        alarm.dismiss()
        locked = false
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                if (store.finishIfDue()) {
                    alarm.cancel()
                    alarm.notifyFinished()
                    if (store.preferences().sound) playChime()
                }
                timer = store.countdown()
                if (timer.phase == Phase.FINISHED) locked = false
                delay(if (timer.phase == Phase.RUNNING) 100 else 500)
            }
        }
    }
    DisposableEffect(settings.keepAwake, timer.phase) {
        if (settings.keepAwake && timer.phase == Phase.RUNNING)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(showSettings || timer.phase != Phase.IDLE) {
        when {
            showSettings -> showSettings = false
            timer.phase == Phase.FINISHED -> reset()
            !locked -> confirmEnd = true
        }
    }

    TimerTheme(settings.color) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Box(Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                when {
                    showSettings -> SettingsScreen(settings, onBack = { showSettings = false }, onSave = {
                        store.save(it); settings = it; showSettings = false
                    })
                    timer.phase == Phase.IDLE -> HomeScreen(selectedMinutes, settings,
                        onMinutes = { selectedMinutes = it.coerceIn(1, 180) },
                        onEdit = { editMinutes = true }, onSettings = { showSettings = true }, onStart = ::start)
                    else -> RunningScreen(timer, now, locked,
                        onLock = { locked = !locked },
                        onPause = {
                            val current = SystemClock.elapsedRealtime()
                            val next = if (timer.phase == Phase.PAUSED) timer.resume(current) else timer.pause(current)
                            if (next.phase != Phase.FINISHED) update(next)
                        },
                        onEnd = { if (timer.phase == Phase.FINISHED) reset() else confirmEnd = true })
                }
            }
        }
        if (editMinutes) MinuteDialog(selectedMinutes, onDismiss = { editMinutes = false }) {
            selectedMinutes = it; editMinutes = false
        }
        if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false },
            title = { Text("Ukončit časovač?") }, text = { Text("Zbývající čas se vynuluje.") },
            confirmButton = { TextButton(onClick = { reset(); confirmEnd = false }) { Text("Ukončit") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Pokračovat") } })
        if (needsExact) AlertDialog(onDismissRequest = { needsExact = false },
            title = { Text("Přesné upozornění") },
            text = { Text("Povolte časovači budíky a připomenutí, aby zazvonil včas i při zhasnutém displeji. Potom se vraťte a spusťte časovač.") },
            confirmButton = { TextButton(onClick = {
                needsExact = false
                if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }) { Text("Otevřít nastavení") } },
            dismissButton = { TextButton(onClick = { needsExact = false }) { Text("Zpět") } })
    }
}

@Composable
private fun ScreenColumn(spacing: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
private fun Header(title: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        action()
    }
}

@Composable
private fun TimerDisk(fraction: Float, modifier: Modifier = Modifier, center: @Composable BoxScope.() -> Unit = {}) {
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.widthIn(max = 330.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawCircle(Track)
            // Elapsed wedge grows clockwise from twelve; at half time the left half remains.
            val remaining = fraction.coerceIn(0f, 1f)
            if (remaining == 1f) drawCircle(color)
            else if (remaining > 0) drawArc(color, -90f + 360f * (1f - remaining), 360f * remaining, true)
        }
        center()
    }
}

@Composable
private fun MainButton(label: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun HomeScreen(minutes: Int, settings: Preferences, onMinutes: (Int) -> Unit,
                       onEdit: () -> Unit, onSettings: () -> Unit, onStart: (Int) -> Unit) {
    val diskSize = if (LocalConfiguration.current.screenHeightDp < 760) 190.dp else 240.dp
    ScreenColumn(spacing = 12.dp) {
        Header("Časovač") {
            TextButton(onClick = onSettings) { Text("Nastavení", fontSize = 16.sp) }
        }
        Text("Kolik času máme?", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        TimerDisk(1f, Modifier.widthIn(max = diskSize)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$minutes", color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold)
                Text("min", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onMinutes(minutes - 1) }, enabled = minutes > 1,
                modifier = Modifier.size(56.dp).semantics { contentDescription = "Odebrat minutu" },
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp)) { Text("−", fontSize = 28.sp) }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                Text("$minutes min", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { onMinutes(minutes + 1) }, enabled = minutes < 180,
                modifier = Modifier.size(56.dp).semantics { contentDescription = "Přidat minutu" },
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp)) { Text("+", fontSize = 28.sp) }
        }
        MainButton("Spustit") { onStart(minutes) }
        Text("Rychlé spuštění", Modifier.fillMaxWidth(), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { col ->
                        val index = row * 2 + col
                        Button(onClick = { onStart(settings.presets[index]) },
                            modifier = Modifier.weight(1f).heightIn(min = 68.dp), shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if ((row + col) % 2 == 0) Color(0xFFEADBFA) else Color(0xFFFFDFC7), contentColor = Ink)) {
                            Text("${settings.presets[index]} min", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningScreen(timer: Countdown, now: Long, locked: Boolean,
                          onLock: () -> Unit, onPause: () -> Unit, onEnd: () -> Unit) {
    val remaining = timer.remaining(now)
    val finished = timer.phase == Phase.FINISHED
    val paused = timer.phase == Phase.PAUSED
    ScreenColumn {
        Header(if (finished) "Čas vypršel" else if (paused) "Pozastaveno" else "Zbývající čas") { }
        if (!finished) {
            if (locked) HoldToUnlock(onLock)
            else TextButton(onClick = onLock, modifier = Modifier.align(Alignment.End)) { Text("Zamknout ovládání") }
        }
        Spacer(Modifier.height(8.dp))
        TimerDisk(if (timer.totalMs == 0L) 0f else remaining.toFloat() / timer.totalMs,
            Modifier.semantics { contentDescription = "Zbývá ${formatRemaining(remaining)} z ${timer.totalMs / 60000} minut" })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatRemaining(remaining), fontSize = 68.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 1)
            Text(if (finished) "Čas na další dobrodružství." else "z ${timer.totalMs / 60000} minut",
                fontSize = 21.sp, color = Muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        if (finished) MainButton("Hotovo", onEnd)
        else if (!locked) {
            OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
                Text(if (paused) "Pokračovat" else "Pozastavit", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ukončit", fontSize = 19.sp) }
        } else Text("Ovládání je zamčené", color = Muted)
    }
}

@Composable
private fun HoldToUnlock(onUnlock: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val currentUnlock by rememberUpdatedState(onUnlock)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0EBE5))
        .semantics {
            contentDescription = "Odemknout ovládání. Podržte tři sekundy."
            role = Role.Button
            customActions = listOf(CustomAccessibilityAction("Odemknout ovládání") { currentUnlock(); true })
        }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                val job = scope.launch {
                    val began = SystemClock.elapsedRealtime()
                    while (SystemClock.elapsedRealtime() - began < 3000) {
                        progress = (SystemClock.elapsedRealtime() - began) / 3000f
                        delay(30)
                    }
                    currentUnlock()
                }
                try { tryAwaitRelease() } finally { job.cancel(); progress = 0f }
            })
        }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pro odemčení podržte 3 sekundy", fontSize = 16.sp)
        if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun SettingsScreen(initial: Preferences, onBack: () -> Unit, onSave: (Preferences) -> Unit) {
    var presets by rememberSaveable { mutableStateOf(initial.presets.map(Int::toString)) }
    var color by rememberSaveable { mutableIntStateOf(initial.color) }
    var sound by rememberSaveable { mutableStateOf(initial.sound) }
    var awake by rememberSaveable { mutableStateOf(initial.keepAwake) }
    val valid = presets.all { it.toIntOrNull() in 1..180 }
    ScreenColumn {
        Header("Nastavení") { TextButton(onClick = onBack) { Text("Zpět") } }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Rychlé volby", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Nastavte čtyři časy od 1 do 180 minut.", color = Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.forEachIndexed { index, value ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Volba ${index + 1}", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value, onValueChange = { text ->
                        if (text.length <= 3 && text.all(Char::isDigit)) presets = presets.toMutableList().also { it[index] = text }
                    }, modifier = Modifier.width(140.dp), singleLine = true,
                        label = { Text("Minuty ${index + 1}") }, suffix = { Text("min") },
                        isError = value.toIntOrNull() !in 1..180,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp))
                }
            }
            if (!valid) Text("Každá volba musí být mezi 1 a 180 minutami.", color = MaterialTheme.colorScheme.error)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Barva časovače", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
                Palette.forEachIndexed { index, shade ->
                    Box(Modifier.size(48.dp).then(if (color == index) Modifier.border(2.dp, Ink, CircleShape) else Modifier)
                        .selectable(selected = color == index, role = Role.RadioButton, onClick = { color = index })
                        .padding(4.dp).clip(CircleShape).background(shade)
                        .semantics { contentDescription = ColorNames[index] }, contentAlignment = Alignment.Center) {
                        if (color == index) Text("✓", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(ColorNames[color], color = Muted)
        }
        SettingSwitch("Zvuk po skončení", sound) { sound = it }
        SettingSwitch("Nechat displej zapnutý", awake) { awake = it }
        Button(onClick = { onSave(Preferences(presets.map(String::toInt), color, sound, awake)) }, enabled = valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette[color])) {
            Text("Uložit", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0EBE5)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), fontSize = 18.sp)
        Switch(checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun MinuteDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Délka časovače") },
        text = {
            OutlinedTextField(text, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) text = it },
                label = { Text("Minuty") }, supportingText = { Text("Zadejte 1 až 180 minut") },
                singleLine = true, isError = value !in 1..180,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        },
        confirmButton = { TextButton(enabled = value in 1..180, onClick = { value?.let(onConfirm) }) { Text("Nastavit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } })
}

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun HomePreview() { TimerTheme(1) { Surface { HomeScreen(15, Preferences(), {}, {}, {}, {}) } } }

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun RunningPreview() { TimerTheme(1) { Surface { RunningScreen(Countdown(Phase.RUNNING, 900000, 900000, 900000), 450000, false, {}, {}, {}) } } }

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun SettingsPreview() { TimerTheme(1) { Surface { SettingsScreen(Preferences(), {}, {}) } } }
