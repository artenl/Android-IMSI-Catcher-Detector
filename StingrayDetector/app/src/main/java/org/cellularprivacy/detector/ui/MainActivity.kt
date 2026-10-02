package org.cellularprivacy.detector.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.cellularprivacy.detector.data.DetectionEventEntity
import org.cellularprivacy.detector.data.DetectorDatabase
import org.cellularprivacy.detector.detect.DetectorState
import org.cellularprivacy.detector.harden.HardeningAdvisor
import org.cellularprivacy.detector.model.ThreatLevel
import org.cellularprivacy.detector.panic.PanicController
import org.cellularprivacy.detector.service.MonitoringService
import org.cellularprivacy.detector.settings.AppSettings
import org.cellularprivacy.detector.settings.PanicAction
import org.cellularprivacy.detector.ui.theme.Term
import org.cellularprivacy.detector.ui.theme.TerminalTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions.launch(permissions)
        setContent { TerminalTheme { DeckScreen() } }
    }
}

private fun color(level: ThreatLevel): Color = when (level) {
    ThreatLevel.NORMAL -> Term.Green
    ThreatLevel.INFO -> Term.Green
    ThreatLevel.SUSPICIOUS -> Term.Amber
    ThreatLevel.HIGH -> Term.Red
}

private fun label(level: ThreatLevel): String = when (level) {
    ThreatLevel.NORMAL -> "SECURE"
    ThreatLevel.INFO -> "OBSERVE"
    ThreatLevel.SUSPICIOUS -> "WATCH"
    ThreatLevel.HIGH -> "THREAT"
}

@Composable
private fun DeckScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val live by DetectorState.state.collectAsState()
    val settings = remember { AppSettings(ctx) }
    val cfg by settings.values.collectAsState(initial = AppSettings.Values())
    val panic = remember { PanicController(ctx) }
    val advisor = remember { HardeningAdvisor(ctx) }

    val db = remember { DetectorDatabase.get(ctx) }
    val events by db.detectionEventDao().recent(100).collectAsState(initial = emptyList())

    var showHarden by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val accent = color(live.level)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Term.Bg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "// IMSI-CATCHER DETECTOR //",
            color = Term.GreenDim,
            fontWeight = FontWeight.Bold
        )

        StatusPanel(live.level, live.score, live.monitoring)

        Text("SERVING: ${live.servingSummary}", color = Term.Muted)

        CommandRow(
            monitoring = live.monitoring,
            accent = accent,
            onArm = { MonitoringService.start(ctx) },
            onDisarm = { MonitoringService.stop(ctx) },
            onHarden = { showHarden = !showHarden; showSettings = false },
            onSettings = { showSettings = !showSettings; showHarden = false },
            onPanic = { panic.execute(PanicAction.CUT_RADIO) }
        )

        when {
            showHarden -> HardenPanel(advisor, onOpen = { intent -> runCatching { ctx.startActivity(intent) } })
            showSettings -> SettingsPanel(
                cfg = cfg,
                adminActive = panic.isDeviceAdminActive(),
                onAuto = { scope.launch { settings.setAutoProtect(it) } },
                onAction = { scope.launch { settings.setPanicAction(it) } },
                onGrantAdmin = { adminLauncher.launch(panic.deviceAdminRequestIntent()) },
                onAirplane = { runCatching { ctx.startActivity(panic.airplaneSettingsIntent()) } }
            )
            else -> EventLog(events)
        }
    }
}

@Composable
private fun StatusPanel(level: ThreatLevel, score: Int, monitoring: Boolean) {
    val accent = color(level)
    // Blink on THREAT.
    val alpha = if (level == ThreatLevel.HIGH) {
        val t = rememberInfiniteTransition(label = "blink")
        t.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label = "blinkA"
        ).value
    } else 1f
    val animated by animateColorAsState(accent, label = "accent")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Term.Surface, RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        Text(
            text = if (monitoring) "[ ${label(level)} ]" else "[ OFFLINE ]",
            color = if (monitoring) animated else Term.Muted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(alpha)
        )
        Spacer(Modifier.height(6.dp))
        Text("SUSPICION SCORE: $score / 100", color = animated)
        ScoreBar(score, animated)
    }
}

@Composable
private fun ScoreBar(score: Int, accent: Color) {
    val filled = (score.coerceIn(0, 100)) / 5  // 20 cells
    val bar = buildString {
        append('[')
        repeat(20) { append(if (it < filled) '#' else '.') }
        append(']')
    }
    Text(bar, color = accent)
}

@Composable
private fun CommandRow(
    monitoring: Boolean,
    accent: Color,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onHarden: () -> Unit,
    onSettings: () -> Unit,
    onPanic: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton(if (monitoring) "DISARM" else "ARM", Term.Green, Modifier.width(120.dp)) {
                if (monitoring) onDisarm() else onArm()
            }
            DeckButton("HARDEN", Term.Green, Modifier.width(120.dp), onClick = onHarden)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton("CONFIG", Term.Green, Modifier.width(120.dp), onClick = onSettings)
            DeckButton("PANIC", Term.Red, Modifier.width(120.dp), onClick = onPanic)
        }
    }
}

@Composable
private fun DeckButton(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
    ) { Text(text) }
}

@Composable
private fun EventLog(events: List<DetectionEventEntity>) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Column(Modifier.fillMaxSize()) {
        Text("> EVENT LOG", color = Term.GreenDim)
        Spacer(Modifier.height(4.dp))
        if (events.isEmpty()) {
            Text("  no events. arm the deck to begin scanning.", color = Term.Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(events) { e ->
                    val c = when (e.threatLevel) {
                        "HIGH" -> Term.Red
                        "SUSPICIOUS" -> Term.Amber
                        else -> Term.Green
                    }
                    Text(
                        "[${fmt.format(Date(e.timestampMs))}] ${e.title} (+${e.score})",
                        color = c
                    )
                    Text("    ${e.detail}", color = Term.Muted)
                }
            }
        }
    }
}

@Composable
private fun HardenPanel(advisor: HardeningAdvisor, onOpen: (Intent) -> Unit) {
    val advice = remember { advisor.buildAdvice() }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("> DEVICE HARDENING", color = Term.GreenDim) }
        items(advice) { a ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Term.Surface, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                val c = if (a.available) Term.Green else Term.Muted
                Text(a.title, color = c, fontWeight = FontWeight.Bold)
                Text(a.rationale, color = Term.Muted)
                if (a.available && a.settingsIntent != null) {
                    Spacer(Modifier.height(6.dp))
                    DeckButton("OPEN SETTINGS", Term.Green) { onOpen(a.settingsIntent) }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    cfg: AppSettings.Values,
    adminActive: Boolean,
    onAuto: (Boolean) -> Unit,
    onAction: (PanicAction) -> Unit,
    onGrantAdmin: () -> Unit,
    onAirplane: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("> CONFIG", color = Term.GreenDim)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-protection (on THREAT)", color = Term.Green)
            Switch(
                checked = cfg.autoProtect, onCheckedChange = onAuto,
                colors = SwitchDefaults.colors(checkedTrackColor = Term.GreenDim)
            )
        }

        Text("Response at THREAT level:", color = Term.Green)
        PanicAction.entries.forEach { action ->
            val selected = cfg.panicAction == action
            DeckButton(
                text = (if (selected) "[x] " else "[ ] ") + actionLabel(action),
                accent = if (action == PanicAction.CUT_RADIO) Term.Red else Term.Green,
                modifier = Modifier.fillMaxWidth()
            ) { onAction(action) }
        }

        Text(
            "Verrouillage immédiat = Device Admin requis. Coupure radio / extinction " +
                "réelle = root requis. Sans root, PANIC alerte, verrouille et ouvre le mode avion.",
            color = Term.Muted
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton(
                if (adminActive) "ADMIN: OK" else "GRANT LOCK",
                if (adminActive) Term.GreenDim else Term.Amber
            ) { onGrantAdmin() }
            DeckButton("AIRPLANE", Term.Green) { onAirplane() }
        }
    }
}

private fun actionLabel(a: PanicAction): String = when (a) {
    PanicAction.NONE -> "NONE (notif seule)"
    PanicAction.ALERT -> "ALERTE (plein ecran + alarme)"
    PanicAction.LOCK -> "VERROU (anti-saisie, ne stoppe PAS la collecte)"
    PanicAction.CUT_RADIO -> "COUPER RADIO (mode avion / extinction si root)"
}
