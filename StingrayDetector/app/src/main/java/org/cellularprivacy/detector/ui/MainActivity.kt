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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.cellularprivacy.detector.data.DetectionEventEntity
import org.cellularprivacy.detector.data.DetectorDatabase
import org.cellularprivacy.detector.data.ObservedCellEntity
import org.cellularprivacy.detector.detect.DetectorState
import org.cellularprivacy.detector.harden.HardeningAdvisor
import org.cellularprivacy.detector.locate.CellLocator
import org.cellularprivacy.detector.locate.Observation
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

internal fun color(level: ThreatLevel): Color = when (level) {
    ThreatLevel.NORMAL, ThreatLevel.INFO -> Term.Green
    ThreatLevel.SUSPICIOUS -> Term.Amber
    ThreatLevel.HIGH -> Term.Red
}

internal fun label(level: ThreatLevel): String = when (level) {
    ThreatLevel.NORMAL -> "OK"
    ThreatLevel.INFO -> "INFO"
    ThreatLevel.SUSPICIOUS -> "SUSPECT"
    ThreatLevel.HIGH -> "MENACE"
}

private const val TAB_STATUS = 0
private const val TAB_MAP = 1
private const val TAB_HARDEN = 2
private const val TAB_CONFIG = 3

@Composable
private fun DeckScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val live by DetectorState.state.collectAsState()
    val settings = remember { AppSettings(ctx) }
    val cfg by settings.values.collectAsState(initial = AppSettings.Values())
    val panic = remember { PanicController(ctx) }
    val advisor = remember { HardeningAdvisor(ctx) }

    val db = remember { DetectorDatabase.get(ctx) }
    val events by db.detectionEventDao().recent(100).collectAsState(initial = emptyList())
    val observed by db.observedCellDao().recent(500).collectAsState(initial = emptyList())
    val radar = remember(observed, events) { buildRadar(observed, events) }

    var tab by remember { mutableIntStateOf(TAB_STATUS) }
    var show2gPrompt by remember { mutableStateOf(false) }
    var showRadioWarn by remember { mutableStateOf(false) }

    val twoG = remember { advisor.buildAdvice().firstOrNull { it.id == "disable_2g" } }
    LaunchedEffect(cfg.hideTwoGPrompt) {
        show2gPrompt = !cfg.hideTwoGPrompt && (twoG?.available == true)
    }

    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    // Starting a location foreground service without the permission crashes on
    // Android 14+, so request it at the moment the user taps start.
    val startLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) MonitoringService.start(ctx) }
    fun armMonitoring() {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) MonitoringService.start(ctx)
        else startLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        containerColor = Term.Bg,
        bottomBar = {
            NavigationBar(containerColor = Term.Surface) {
                navItem("STATUT", "◎", tab == TAB_STATUS) { tab = TAB_STATUS }
                navItem("CARTE", "◈", tab == TAB_MAP) { tab = TAB_MAP }
                navItem("DURCIR", "⚙", tab == TAB_HARDEN) { tab = TAB_HARDEN }
                navItem("CONFIG", "≡", tab == TAB_CONFIG) { tab = TAB_CONFIG }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(Term.Bg)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("// STINGRAY FUZZ //", color = Term.GreenDim, fontWeight = FontWeight.Bold)
            StatusPanel(live.level, live.score, live.monitoring)
            Text("CELLULE: ${live.servingSummary}", color = Term.Muted)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    TAB_STATUS -> StatusTab(
                        monitoring = live.monitoring,
                        zoneMode = live.zoneMode,
                        events = events,
                        onStart = { armMonitoring() },
                        onStop = { MonitoringService.stop(ctx) },
                        onZone = { DetectorState.setZoneMode(!live.zoneMode) },
                        onPanic = { panic.execute(PanicAction.CUT_RADIO) }
                    )
                    TAB_MAP -> MapScreen(radar.first?.first, radar.first?.second, radar.second)
                    TAB_HARDEN -> HardenPanel(advisor) { intent -> runCatching { ctx.startActivity(intent) } }
                    TAB_CONFIG -> SettingsPanel(
                        cfg = cfg,
                        adminActive = panic.isDeviceAdminActive(),
                        onAuto = { scope.launch { settings.setAutoProtect(it) } },
                        onAction = { scope.launch { settings.setPanicAction(it) } },
                        onExpert = { scope.launch { settings.setExpertMode(it) } },
                        onGrantAdmin = { adminLauncher.launch(panic.deviceAdminRequestIntent()) },
                        onAirplane = { runCatching { ctx.startActivity(panic.airplaneSettingsIntent()) } },
                        onRadioInfo = { showRadioWarn = true }
                    )
                }
            }
        }
    }

    if (show2gPrompt && twoG != null) {
        AlertDialog(
            containerColor = Term.Surface,
            titleContentColor = Term.Green,
            textContentColor = Term.Muted,
            onDismissRequest = { show2gPrompt = false },
            title = { Text("PROTECTION: DESACTIVER LA 2G") },
            text = {
                Text(
                    twoG.rationale +
                        "\n\nC'est la meilleure protection preventive : elle bloque la " +
                        "retrogradation forcee des IMSI-catchers sans couper votre reseau.\n\n" +
                        (twoG.steps ?: "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    show2gPrompt = false
                    twoG.settingsIntent?.let { runCatching { ctx.startActivity(it) } }
                }) { Text("OUVRIR LES REGLAGES", color = Term.Green) }
            },
            dismissButton = {
                TextButton(onClick = {
                    show2gPrompt = false
                    scope.launch { settings.setHideTwoGPrompt(true) }
                }) { Text("NE PLUS PROPOSER", color = Term.Muted) }
            }
        )
    }

    if (showRadioWarn) {
        AlertDialog(
            containerColor = Term.Surface,
            titleContentColor = Term.Amber,
            textContentColor = Term.Muted,
            onDismissRequest = { showRadioWarn = false },
            title = { Text("RADIO INFO — ATTENTION") },
            text = {
                Text(
                    "Cet ecran d'ingenierie affiche l'etat radio detaille (bandes, NR, mode " +
                        "reseau), mais AUSSI des identifiants sensibles : IMEI, IMSI et numero. " +
                        "Ne le capture pas et ne le partage pas."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRadioWarn = false
                    runCatching { ctx.startActivity(org.cellularprivacy.detector.util.ExpertTools.radioInfoIntent()) }
                }) { Text("OUVRIR QUAND MEME", color = Term.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showRadioWarn = false }) { Text("ANNULER", color = Term.Muted) }
            }
        )
    }
}

@Composable
private fun RowScope.navItem(
    label: String,
    glyph: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(glyph, fontSize = 16.sp) },
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Term.Bg,
            selectedTextColor = Term.Green,
            indicatorColor = Term.Green,
            unselectedIconColor = Term.Muted,
            unselectedTextColor = Term.Muted
        )
    )
}

@Composable
private fun StatusTab(
    monitoring: Boolean,
    zoneMode: Boolean,
    events: List<DetectionEventEntity>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onZone: () -> Unit,
    onPanic: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DeckButton(
            text = if (monitoring) "ARRETER LA SURVEILLANCE" else "DEMARRER LA SURVEILLANCE",
            accent = if (monitoring) Term.Amber else Term.Green,
            modifier = Modifier.fillMaxWidth()
        ) { if (monitoring) onStop() else onStart() }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DeckButton(
                text = if (zoneMode) "ZONE: ON" else "ZONE: OFF",
                accent = if (zoneMode) Term.Amber else Term.GreenDim,
                modifier = Modifier.weight(1f)
            ) { onZone() }
            DeckButton("PANIC", Term.Red, Modifier.weight(1f)) { onPanic() }
        }

        if (zoneMode) {
            Text("Zone armee : toute suspicion coupera la radio.", color = Term.Amber)
        }

        EventLog(events)
    }
}

@Composable
private fun StatusPanel(level: ThreatLevel, score: Int, monitoring: Boolean) {
    val accent = color(level)
    val alpha = if (level == ThreatLevel.HIGH && monitoring) {
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
            text = if (monitoring) "[ ${label(level)} ]" else "[ EN VEILLE ]",
            color = if (monitoring) animated else Term.Muted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(alpha)
        )
        Spacer(Modifier.height(6.dp))
        Text("SUSPICION: $score / 100", color = if (monitoring) animated else Term.Muted)
        ScoreBar(score, if (monitoring) animated else Term.Muted)
    }
}

@Composable
private fun ScoreBar(score: Int, accent: Color) {
    val filled = score.coerceIn(0, 100) / 5
    val bar = buildString {
        append('[')
        repeat(20) { append(if (it < filled) '#' else '.') }
        append(']')
    }
    Text(bar, color = accent)
}

@Composable
internal fun DeckButton(
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
    Text("> JOURNAL", color = Term.GreenDim)
    if (events.isEmpty()) {
        Text("  Aucun evenement. Demarre la surveillance pour commencer.", color = Term.Muted)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
            items(events) { e ->
                val c = when (e.threatLevel) {
                    "HIGH" -> Term.Red
                    "SUSPICIOUS" -> Term.Amber
                    else -> Term.Green
                }
                Text("[${fmt.format(Date(e.timestampMs))}] ${e.title} (+${e.score})", color = c)
                Text("    ${e.detail}", color = Term.Muted)
            }
        }
    }
}

@Composable
private fun HardenPanel(advisor: HardeningAdvisor, onOpen: (Intent) -> Unit) {
    val advice = remember { advisor.buildAdvice() }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("> DURCISSEMENT", color = Term.GreenDim) }
        items(advice) { a ->
            Column(
                Modifier.fillMaxWidth().background(Term.Surface, RoundedCornerShape(6.dp)).padding(12.dp)
            ) {
                val c = if (a.available) Term.Green else Term.Muted
                Text(a.title, color = c, fontWeight = FontWeight.Bold)
                Text(a.rationale, color = Term.Muted)
                a.steps?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Term.GreenDim)
                }
                if (a.available && a.settingsIntent != null) {
                    Spacer(Modifier.height(6.dp))
                    DeckButton("OUVRIR LES REGLAGES", Term.Green, Modifier.fillMaxWidth()) {
                        onOpen(a.settingsIntent)
                    }
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
    onExpert: (Boolean) -> Unit,
    onGrantAdmin: () -> Unit,
    onAirplane: () -> Unit,
    onRadioInfo: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("> CONFIG", color = Term.GreenDim)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-protection (sur MENACE)", color = Term.Green)
            Switch(
                checked = cfg.autoProtect, onCheckedChange = onAuto,
                colors = SwitchDefaults.colors(checkedTrackColor = Term.GreenDim)
            )
        }

        Text("Reponse au niveau MENACE :", color = Term.Green)
        PanicAction.entries.forEach { action ->
            val selected = cfg.panicAction == action
            DeckButton(
                text = (if (selected) "[x] " else "[ ] ") + actionLabel(action),
                accent = if (action == PanicAction.CUT_RADIO) Term.Red else Term.Green,
                modifier = Modifier.fillMaxWidth()
            ) { onAction(action) }
        }

        Text(
            "Verrouillage = anti-saisie (ne stoppe pas la collecte). Couper la radio = " +
                "mode avion/extinction (root requis), sinon alerte + ouverture du mode avion.",
            color = Term.Muted
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton(
                if (adminActive) "VERROU: OK" else "ACTIVER VERROU",
                if (adminActive) Term.GreenDim else Term.Amber,
                Modifier.weight(1f)
            ) { onGrantAdmin() }
            DeckButton("MODE AVION", Term.Green, Modifier.weight(1f)) { onAirplane() }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mode expert", color = Term.Amber)
            Switch(
                checked = cfg.expertMode, onCheckedChange = onExpert,
                colors = SwitchDefaults.colors(checkedTrackColor = Term.Amber)
            )
        }
        if (cfg.expertMode) {
            Text(
                "Ecrans d'ingenierie avances. Affichent aussi des identifiants sensibles " +
                    "(IMEI/IMSI). A n'utiliser que si tu sais ce que tu fais.",
                color = Term.Muted
            )
            DeckButton("RADIO INFO", Term.Amber, Modifier.fillMaxWidth()) { onRadioInfo() }
        }
    }
}

private fun actionLabel(a: PanicAction): String = when (a) {
    PanicAction.NONE -> "Aucune (notif seule)"
    PanicAction.ALERT -> "Alerte (plein ecran + alarme)"
    PanicAction.LOCK -> "Verrouiller (anti-saisie)"
    PanicAction.CUT_RADIO -> "Couper la radio (recommande)"
}

/** Build the offline radar model from stored observations and detection events. */
private fun buildRadar(
    observed: List<ObservedCellEntity>,
    events: List<DetectionEventEntity>
): Pair<Pair<Double, Double>?, List<PlacedCell>> {
    val withLoc = observed.filter { it.lat != null && it.lon != null }
    val user = withLoc.maxByOrNull { it.timestampMs }?.let { it.lat!! to it.lon!! }

    val levelByCell: Map<String, ThreatLevel> = events
        .filter { it.cellKey != null }
        .groupBy { it.cellKey!! }
        .mapValues { (_, evs) ->
            evs.mapNotNull { runCatching { ThreatLevel.valueOf(it.threatLevel) }.getOrNull() }
                .maxByOrNull { it.ordinal } ?: ThreatLevel.NORMAL
        }

    val placed = withLoc.groupBy { it.cellKey }.mapNotNull { (key, obs) ->
        val est = CellLocator.estimate(obs.map { Observation(it.lat!!, it.lon!!, it.dbm) })
            ?: return@mapNotNull null
        val last = obs.maxByOrNull { it.timestampMs }!!
        PlacedCell("${last.rat} CID:${last.cellId ?: "?"}", est, levelByCell[key] ?: ThreatLevel.NORMAL)
    }
    return user to placed
}
