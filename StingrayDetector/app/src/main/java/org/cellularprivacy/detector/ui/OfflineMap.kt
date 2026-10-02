package org.cellularprivacy.detector.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cellularprivacy.detector.ui.theme.Term
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.math.cos

private fun dot(argb: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(argb)
    setSize(40, 40)
    setStroke(3, 0xFF000000.toInt())
}

/**
 * Offline OpenStreetMap view. Tiles render only if pre-downloaded; live fetching
 * stays off except during an explicit download. The user picks an address and a
 * radius in metres, so they can cache exactly the area they will need, ahead of
 * time and outside any sensitive zone.
 */
@Composable
fun OfflineMap(userLat: Double?, userLon: Double?, cells: List<PlacedCell>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val map = remember {
        Configuration.getInstance()
            .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(false)
            controller.setZoom(14.0)
        }
    }

    var address by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("2000") }
    var status by remember { mutableStateOf("Choisis une adresse et un rayon, puis telecharge (a l'avance, hors zone sensible).") }
    var showWarn by remember { mutableStateOf(false) }

    LaunchedEffect(userLat, userLon, cells) {
        val center = if (userLat != null && userLon != null) GeoPoint(userLat, userLon) else null
        center?.let { map.controller.setCenter(it) }
        refreshMarkers(map, center, cells)
    }

    DisposableEffect(Unit) { onDispose { runCatching { map.onDetach() } } }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = Term.Green, unfocusedTextColor = Term.Green,
        focusedContainerColor = Term.Surface, unfocusedContainerColor = Term.Surface,
        focusedLabelColor = Term.GreenDim, unfocusedLabelColor = Term.Muted,
        cursorColor = Term.Green
    )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("> CARTE HORS-LIGNE", color = Term.GreenDim)

        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Adresse (ex: 1 av des Champs-Elysees, Paris)") },
            singleLine = true, colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = radius, onValueChange = { radius = it.filter { c -> c.isDigit() } },
                label = { Text("Rayon (m)") }, singleLine = true, colors = fieldColors,
                modifier = Modifier.width(140.dp)
            )
            DeckButton("ALLER", Term.Green, Modifier.weight(1f)) {
                scope.launch {
                    val p = geocode(ctx, address)
                    if (p == null) {
                        status = "Adresse introuvable (verifie l'orthographe / la connexion)."
                    } else {
                        map.controller.setZoom(15.0)
                        map.controller.setCenter(p)
                        refreshMarkers(map, p, cells)
                        status = "Centre sur: ${address}. Pret a telecharger ${radius} m autour."
                    }
                }
            }
        }
        DeckButton("TELECHARGER CETTE ZONE", Term.Amber, Modifier.fillMaxWidth()) { showWarn = true }

        Text(status, color = Term.Muted)

        AndroidView(
            factory = { map },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).weight(1f)
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
                    "Zone: ${r} m autour du centre de la carte. Fais-le A L'AVANCE et HORS " +
                        "d'une zone sensible : ce telechargement utilise le reseau et revele ta " +
                        "zone au serveur de tuiles. Ensuite la carte fonctionne hors-ligne."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarn = false
                    downloadRadius(ctx, map, r) { status = it }
                }) { Text("TELECHARGER MAINTENANT", color = Term.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showWarn = false }) { Text("ANNULER", color = Term.Muted) }
            }
        )
    }
}

private fun refreshMarkers(map: MapView, center: GeoPoint?, cells: List<PlacedCell>) {
    map.overlays.clear()
    center?.let {
        map.overlays.add(Marker(map).apply {
            position = it; setAnchor(0.5f, 0.5f); icon = dot(Term.Green.toArgb()); title = "Centre"
        })
    }
    cells.forEach { c ->
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(c.estimate.lat, c.estimate.lon)
            setAnchor(0.5f, 0.5f)
            icon = dot(levelColor(c.level).toArgb())
            title = "${c.label} +/-${c.estimate.accuracyMeters.toInt()}m"
        })
    }
    map.invalidate()
}

/** Address -> coordinates via the framework geocoder (network, off the main thread). */
private suspend fun geocode(ctx: Context, query: String): GeoPoint? {
    if (query.isBlank() || !Geocoder.isPresent()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.getDefault()).getFromLocationName(query, 1)
                ?.firstOrNull()
                ?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
    }
}

/** Download a box of the given radius (metres) around the current map centre. */
private fun downloadRadius(ctx: Context, map: MapView, radiusM: Int, onStatus: (String) -> Unit) {
    val c = map.mapCenter
    val latSpan = radiusM / 111_320.0
    val lonSpan = radiusM / (111_320.0 * cos(Math.toRadians(c.latitude)))
    val bb = BoundingBox(
        c.latitude + latSpan, c.longitude + lonSpan,
        c.latitude - latSpan, c.longitude - lonSpan
    )
    map.setUseDataConnection(true)
    val cm = CacheManager(map)
    cm.downloadAreaAsync(ctx, bb, 12, 16, object : CacheManager.CacheManagerCallback {
        override fun onTaskComplete() {
            map.setUseDataConnection(false); onStatus("Carte telechargee. Mode hors-ligne reactive.")
        }
        override fun onTaskFailed(errors: Int) {
            map.setUseDataConnection(false); onStatus("Termine avec $errors erreurs. Hors-ligne reactive.")
        }
        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
            onStatus("Telechargement $progress% (zoom $currentZoomLevel/$zoomMax)")
        }
        override fun downloadStarted() { onStatus("Telechargement demarre...") }
        override fun setPossibleTilesInArea(total: Int) { onStatus("Tuiles a recuperer: $total") }
    })
}
