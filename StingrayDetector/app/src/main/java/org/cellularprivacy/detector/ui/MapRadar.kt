package org.cellularprivacy.detector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.cellularprivacy.detector.locate.CellEstimate
import org.cellularprivacy.detector.model.ThreatLevel
import org.cellularprivacy.detector.ui.theme.Term
import org.cellularprivacy.detector.util.Geo
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A cell placed on the radar: its estimate, a label, and its suspicion level. */
data class PlacedCell(
    val label: String,
    val estimate: CellEstimate,
    val level: ThreatLevel
)

internal fun levelColor(level: ThreatLevel): Color = when (level) {
    ThreatLevel.NORMAL, ThreatLevel.INFO -> Term.Green
    ThreatLevel.SUSPICIOUS -> Term.Amber
    ThreatLevel.HIGH -> Term.Red
}

/**
 * Offline "radar" view: the user at centre, each estimated cell plotted by
 * bearing and distance, coloured by suspicion. No map tiles, no network.
 */
@Composable
fun MapRadar(
    userLat: Double?,
    userLon: Double?,
    cells: List<PlacedCell>
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("> RADAR (hors-ligne, position estimee)", color = Term.GreenDim)

        if (userLat == null || userLon == null) {
            Text(
                "Position inconnue. Active la localisation et lance la surveillance " +
                    "pour accumuler des mesures.",
                color = Term.Muted
            )
            return@Column
        }

        // Distance of each cell from the user; scale the farthest to the edge.
        val dists = cells.map { Geo.haversine(userLat, userLon, it.estimate.lat, it.estimate.lon) }
        val maxDist = (dists.maxOrNull() ?: 1000.0).coerceIn(300.0, 20_000.0)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(4.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rMax = minOf(cx, cy) * 0.92f

            // Range rings at 1/3, 2/3, 3/3.
            for (k in 1..3) {
                drawCircle(
                    color = Term.GreenDim.copy(alpha = 0.5f),
                    radius = rMax * k / 3f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
            }
            // Cross hair.
            drawLine(Term.GreenDim.copy(alpha = 0.4f), Offset(cx, cy - rMax), Offset(cx, cy + rMax), 1f)
            drawLine(Term.GreenDim.copy(alpha = 0.4f), Offset(cx - rMax, cy), Offset(cx + rMax, cy), 1f)

            // User at centre.
            drawCircle(Term.Green, radius = 7f, center = Offset(cx, cy))

            // Cells.
            for (i in cells.indices) {
                val c = cells[i]
                val d = dists[i]
                val r = (d / maxDist).toFloat() * rMax
                val brg = Math.toRadians(
                    Geo.bearing(userLat, userLon, c.estimate.lat, c.estimate.lon)
                )
                val x = cx + (r * sin(brg)).toFloat()
                val y = cy - (r * cos(brg)).toFloat()
                val col = levelColor(c.level)
                // Accuracy halo.
                val haloR = (c.estimate.accuracyMeters / maxDist).toFloat() * rMax
                drawCircle(col.copy(alpha = 0.15f), radius = max(haloR, 6f), center = Offset(x, y))
                drawCircle(col, radius = 6f, center = Offset(x, y))
            }
        }

        Text("Portee: ~${maxDist.toInt()} m au bord", color = Term.Muted)

        // Detail list under the radar (labels, distance, accuracy, level).
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(cells.sortedByDescending { it.level.ordinal }) { c ->
                val d = Geo.haversine(userLat, userLon, c.estimate.lat, c.estimate.lon)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.size(12.dp).padding(top = 3.dp)) {
                        drawCircle(levelColor(c.level), radius = 5f, center = Offset(size.width / 2, size.height / 2))
                    }
                    Column {
                        Text(c.label, color = levelColor(c.level))
                        Text(
                            "~${d.toInt()} m, +/-${c.estimate.accuracyMeters.toInt()} m, " +
                                "${c.estimate.samples} mesures",
                            color = Term.Muted
                        )
                    }
                }
            }
        }
    }
}
