package org.cellularprivacy.detector.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.cellularprivacy.detector.ui.theme.Term
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private fun dot(argb: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(argb)
    setSize(40, 40)
    setStroke(3, 0xFF000000.toInt())
}

/**
 * Offline OpenStreetMap view. Tiles are only shown if they were pre-downloaded;
 * live fetching stays OFF (setUseDataConnection(false)) except during an
 * explicit, user-initiated download. Estimated cells are drawn as markers
 * coloured by suspicion. This is why the download must be done ahead of time
 * and outside sensitive zones: it is the only moment the map touches the network.
 */
@Composable
fun OfflineMap(userLat: Double?, userLon: Double?, cells: List<PlacedCell>) {
    val ctx = LocalContext.current

    val map = remember {
        Configuration.getInstance()
            .load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(false)   // offline by default
            controller.setZoom(14.0)
        }
    }

    var status by remember { mutableStateOf("Carte hors-ligne. Tuiles non telechargees = fond gris.") }
    var showWarn by remember { mutableStateOf(false) }

    LaunchedEffect(userLat, userLon, cells) {
        val center = if (userLat != null && userLon != null) GeoPoint(userLat, userLon) else null
        center?.let { map.controller.setCenter(it) }
        map.overlays.clear()
        center?.let {
            map.overlays.add(Marker(map).apply {
                position = it; setAnchor(0.5f, 0.5f)
                icon = dot(Term.Green.toArgb()); title = "Vous"
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

    DisposableEffect(Unit) { onDispose { runCatching { map.onDetach() } } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("> CARTE HORS-LIGNE", color = Term.GreenDim)
        DeckButton("TELECHARGER CETTE ZONE", Term.Amber, Modifier.fillMaxWidth()) { showWarn = true }
        Text(status, color = Term.Muted)
        AndroidView(
            factory = { map },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).weight(1f)
        )
    }

    if (showWarn) {
        AlertDialog(
            containerColor = Term.Surface,
            titleContentColor = Term.Amber,
            textContentColor = Term.Muted,
            onDismissRequest = { showWarn = false },
            title = { Text("TELECHARGER LA CARTE") },
            text = {
                Text(
                    "Fais-le A L'AVANCE et HORS d'une zone sensible. Ce telechargement " +
                        "utilise le reseau et revele ta zone approximative au serveur de " +
                        "tuiles. Ensuite, la carte fonctionne entierement hors-ligne."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarn = false
                    downloadAround(ctx, map) { status = it }
                }) { Text("TELECHARGER MAINTENANT", color = Term.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showWarn = false }) {
                    Text("ANNULER", color = Term.Muted)
                }
            }
        )
    }
}

/** Download a ~6 km box around the current map centre for offline use. */
private fun downloadAround(ctx: Context, map: MapView, onStatus: (String) -> Unit) {
    val c = map.mapCenter
    val latSpan = 0.03   // ~3.3 km
    val lonSpan = 0.045
    val bb = BoundingBox(
        c.latitude + latSpan, c.longitude + lonSpan,
        c.latitude - latSpan, c.longitude - lonSpan
    )
    map.setUseDataConnection(true)
    val cm = CacheManager(map)
    cm.downloadAreaAsync(ctx, bb, 12, 16, object : CacheManager.CacheManagerCallback {
        override fun onTaskComplete() {
            map.setUseDataConnection(false)
            onStatus("Carte telechargee. Mode hors-ligne reactive.")
        }
        override fun onTaskFailed(errors: Int) {
            map.setUseDataConnection(false)
            onStatus("Termine avec $errors erreurs. Mode hors-ligne reactive.")
        }
        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
            onStatus("Telechargement $progress% (zoom $currentZoomLevel/$zoomMax)")
        }
        override fun downloadStarted() { onStatus("Telechargement demarre...") }
        override fun setPossibleTilesInArea(total: Int) { onStatus("Tuiles a recuperer: $total") }
    })
}
