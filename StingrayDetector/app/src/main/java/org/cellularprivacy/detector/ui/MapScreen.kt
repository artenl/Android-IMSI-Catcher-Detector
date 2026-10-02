package org.cellularprivacy.detector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.cellularprivacy.detector.ui.theme.Term

/**
 * Container for the two map views:
 *  - RADAR: fully offline, needs no download (self-drawn from own measurements).
 *  - CARTE: OpenStreetMap, offline once tiles are pre-downloaded.
 */
@Composable
fun MapScreen(userLat: Double?, userLon: Double?, cells: List<PlacedCell>) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton(
                text = if (tab == 0) "[RADAR]" else "RADAR",
                accent = Term.Green
            ) { tab = 0 }
            DeckButton(
                text = if (tab == 1) "[CARTE]" else "CARTE",
                accent = Term.Green
            ) { tab = 1 }
        }
        Column(Modifier.weight(1f)) {
            if (tab == 0) MapRadar(userLat, userLon, cells)
            else OfflineMap(userLat, userLon, cells)
        }
    }
}
