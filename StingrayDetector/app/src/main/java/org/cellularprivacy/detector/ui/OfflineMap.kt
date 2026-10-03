package org.cellularprivacy.detector.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.cellularprivacy.detector.anfr.AnfrClient
import org.cellularprivacy.detector.data.AnfrSiteEntity
import org.cellularprivacy.detector.data.DetectorDatabase
import org.cellularprivacy.detector.ui.theme.Term
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import kotlin.math.cos

private val ANFR_BLUE = Color(0xFF4FA3FF)

private fun bulkOsmSource(): XYTileSource = XYTileSource(
    "OSM", 0, 19, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.org/",
        "https://b.tile.openstreetmap.org/",
        "https://c.tile.openstreetmap.org/"
    ),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
    )
)

private fun configuredMap(ctx: Context, showZoomButtons: Boolean = false): MapView {
    val conf = Configuration.getInstance()
    conf.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    conf.userAgentValue = ctx.packageName
    val base = File(ctx.cacheDir, "osmdroid").apply { mkdirs() }
    conf.osmdroidBasePath = base
    conf.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
    return MapView(ctx).apply {
        setTileSource(bulkOsmSource())
        setUseDataConnection(true)
        isTilesScaledToDpi = true
        setMultiTouchControls(true)
        zoomController.setVisibility(
            if (showZoomButtons) CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
            else CustomZoomButtonsController.Visibility.NEVER
        )
        minZoomLevel = 4.0
        maxZoomLevel = 19.0
        setHorizontalMapRepetitionEnabled(false)
        setVerticalMapRepetitionEnabled(false)
        controller.setZoom(15.0)
        controller.setCenter(GeoPoint(48.8566, 2.3522))
    }
}

private fun dot(argb: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(argb)
    setSize(40, 40)
    setStroke(3, 0xFF000000.toInt())
}

/**
 * CARTE tab: a fully scrollable control panel (no inline map, so nothing steals
 * drags or hides buttons). Pick an address + radius, cache the tiles and the
 * ANFR official sites for that area, then open the map full-screen to browse.
 */
@Composable
fun OfflineMap(userLat: Double?, userLon: Double?, cells: List<PlacedCell>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DetectorDatabase.get(ctx) }
    val anfrSites by db.anfrSiteDao().all().collectAsState(initial = emptyList())

    // Unattached map instance, used only for tile bulk-download (CacheManager).
    val downloadMap = remember { configuredMap(ctx) }
    DisposableEffect(Unit) { onDispose { runCatching { downloadMap.onDetach() } } }

    var address by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("2000") }
    var status by remember {
        mutableStateOf("Choisis une adresse + rayon, telecharge (a l'avance, hors zone sensible), puis ouvre la carte.")
    }
    var targetLat by remember { mutableStateOf(userLat) }
    var targetLon by remember { mutableStateOf(userLon) }
    var showWarn by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = Term.Green, unfocusedTextColor = Term.Green,
        focusedContainerColor = Term.Surface, unfocusedContainerColor = Term.Surface,
        focusedLabelColor = Term.GreenDim, unfocusedLabelColor = Term.Muted,
        cursorColor = Term.Green
    )

    fun center(): Pair<Double, Double>? {
        val la = targetLat ?: userLat
        val lo = targetLon ?: userLon
        return if (la != null && lo != null) la to lo else null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("> CARTE HORS-LIGNE", color = Term.GreenDim)

        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Adresse / ville (ex: 1 av des Champs-Elysees, Paris)") },
            singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = radius, onValueChange = { radius = it.filter { c -> c.isDigit() } },
                label = { Text("Rayon (m)") }, singleLine = true, colors = fieldColors,
                modifier = Modifier.width(140.dp)
            )
            DeckButton("LOCALISER", Term.Green, Modifier.weight(1f)) {
                scope.launch {
                    val p = geocode(ctx, address)
                    if (p == null) status = "Adresse introuvable (verifie l'orthographe / la connexion)."
                    else {
                        targetLat = p.latitude; targetLon = p.longitude
                        status = "Zone reglee sur: $address. Telecharge puis ouvre la carte."
                    }
                }
            }
        }

        DeckButton("TELECHARGER CETTE ZONE", Term.Amber, Modifier.fillMaxWidth()) {
            if (center() == null) status = "Renseigne une adresse et LOCALISER d'abord."
            else showWarn = true
        }

        DeckButton("AJOUTER ANTENNES ANFR (${anfrSites.size})", ANFR_BLUE, Modifier.fillMaxWidth()) {
            val c = center()
            if (c == null) {
                status = "Renseigne une adresse et LOCALISER d'abord."
            } else {
                val r = radius.toIntOrNull() ?: 2000
                val label = address.ifBlank { "zone" }
                status = "ANFR : telechargement autour de $label..."
                scope.launch {
                    val res = AnfrClient.fetchAround(c.first, c.second, r, label)
                    status = if (res.error != null) "ANFR erreur: ${res.error}"
                    else {
                        db.anfrSiteDao().insertAll(res.sites)
                        "ANFR : ${res.sites.size} sites ajoutes pour $label" +
                            (if (res.truncated) " (zone dense, tronquee - reduis le rayon)" else "") +
                            ". Points bleus sur la carte."
                    }
                }
            }
        }
        if (anfrSites.isNotEmpty()) {
            DeckButton("VIDER LES ANTENNES ANFR", Term.Muted, Modifier.fillMaxWidth()) {
                scope.launch { db.anfrSiteDao().clear() }
            }
        }

        DeckButton("OUVRIR LA CARTE", Term.Green, Modifier.fillMaxWidth()) {
            if (center() == null) status = "Renseigne une adresse et LOCALISER, ou demarre la surveillance pour un point GPS."
            else fullscreen = true
        }

        Text(status, color = Term.Muted)
        Text(
            "Antennes ANFR stockees: ${anfrSites.size}. La carte s'ouvre en plein ecran " +
                "pour circuler librement.",
            color = Term.Muted
        )
    }

    if (showWarn) {
        val r = radius.toIntOrNull() ?: 2000
        AlertDialog(
            containerColor = Term.Surface,
            titleContentColor = Term.Amber,
            textContentColor = Term.Muted,
            onDismissRequest = { showWarn = false },
            title = { Text("TELECHARGER ${r} m") },
            text = {
                Text(
                    "Zone: ${r} m autour de l'adresse. Fais-le A L'AVANCE et HORS d'une zone " +
                        "sensible : ce telechargement utilise le reseau et revele ta zone au " +
                        "serveur de tuiles. Ensuite la carte fonctionne hors-ligne."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarn = false
                    val c = center()
                    if (c != null) downloadRadius(ctx, downloadMap, c.first, c.second, r) { status = it }
                }) { Text("TELECHARGER MAINTENANT", color = Term.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showWarn = false }) { Text("ANNULER", color = Term.Muted) }
            }
        )
    }

    if (fullscreen) {
        val c = center()
        FullscreenMap(c?.first, c?.second, cells, anfrSites) { fullscreen = false }
    }
}

private fun refreshMarkers(
    map: MapView,
    center: GeoPoint?,
    cells: List<PlacedCell>,
    anfr: List<AnfrSiteEntity>
) {
    map.overlays.clear()
    anfr.forEach { site ->
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(site.lat, site.lon); setAnchor(0.5f, 0.5f)
            icon = dot(ANFR_BLUE.toArgb()); title = "ANFR ${site.operators} (${site.generations})"
        })
    }
    center?.let {
        map.overlays.add(Marker(map).apply {
            position = it; setAnchor(0.5f, 0.5f); icon = dot(Term.Green.toArgb()); title = "Centre"
        })
    }
    cells.forEach { c ->
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(c.estimate.lat, c.estimate.lon); setAnchor(0.5f, 0.5f)
            icon = dot(levelColor(c.level).toArgb())
            title = "${c.label} +/-${c.estimate.accuracyMeters.toInt()}m"
        })
    }
    map.invalidate()
}

private suspend fun geocode(ctx: Context, query: String): GeoPoint? {
    if (query.isBlank() || !Geocoder.isPresent()) return null
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.getDefault()).getFromLocationName(query, 1)
                ?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
    }
}

private fun downloadRadius(
    ctx: Context, map: MapView, lat: Double, lon: Double, radiusM: Int, onStatus: (String) -> Unit
) {
    try {
        val latSpan = radiusM / 111_320.0
        val lonSpan = radiusM / (111_320.0 * cos(Math.toRadians(lat)))
        val bb = BoundingBox(lat + latSpan, lon + lonSpan, lat - latSpan, lon - lonSpan)
        val cm = CacheManager(map)
        cm.downloadAreaAsync(ctx, bb, 12, 16, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() = onStatus("Zone telechargee. Visible hors-ligne dans la carte.")
            override fun onTaskFailed(errors: Int) = onStatus("Termine avec $errors erreurs (tuiles manquantes).")
            override fun updateProgress(progress: Int, z: Int, zMin: Int, zMax: Int) =
                onStatus("Telechargement $progress% (zoom $z/$zMax)")
            override fun downloadStarted() = onStatus("Telechargement demarre...")
            override fun setPossibleTilesInArea(total: Int) = onStatus("Tuiles a recuperer: $total")
        })
    } catch (t: Throwable) {
        onStatus("Erreur telechargement: ${t.message ?: t.javaClass.simpleName}")
    }
}

/** Full-screen map in a borderless dialog, for unobstructed panning and zoom. */
@Composable
private fun FullscreenMap(
    centerLat: Double?,
    centerLon: Double?,
    cells: List<PlacedCell>,
    anfr: List<AnfrSiteEntity>,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val map = remember { configuredMap(ctx, showZoomButtons = true) }

    LaunchedEffect(Unit) {
        runCatching { map.onResume() }
        if (centerLat != null && centerLon != null) {
            map.controller.setCenter(GeoPoint(centerLat, centerLon))
        }
    }
    val centerPoint = if (centerLat != null && centerLon != null) GeoPoint(centerLat, centerLon) else null
    LaunchedEffect(cells, anfr, centerPoint) { refreshMarkers(map, centerPoint, cells, anfr) }
    DisposableEffect(Unit) {
        onDispose { runCatching { map.onPause() }; runCatching { map.onDetach() } }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Term.Bg)) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            DeckButton("FERMER", Term.Green, Modifier.align(Alignment.TopEnd).padding(12.dp)) { onClose() }
        }
    }
}
