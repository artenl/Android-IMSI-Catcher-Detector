package org.cellularprivacy.detector.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.cellularprivacy.detector.harden.HardeningAdvisor
import org.cellularprivacy.detector.service.MonitoringService

/**
 * Minimal first-slice UI: request the location + notification permissions,
 * start/stop monitoring, and show the hardening checklist. A richer Compose UI
 * (live cells, map, event log) comes in a later slice.
 */
class MainActivity : ComponentActivity() {

    private val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions.launch(permissions)

        val advisor = HardeningAdvisor(this)
        setContent {
            MaterialTheme {
                Screen(
                    onStart = { MonitoringService.start(this) },
                    onStop = { MonitoringService.stop(this) },
                    advisor = advisor
                )
            }
        }
    }
}

@Composable
private fun Screen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    advisor: HardeningAdvisor
) {
    var monitoring by remember { mutableStateOf(false) }
    val advice = remember { advisor.buildAdvice() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("IMSI-Catcher Detector", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = {
            if (monitoring) onStop() else onStart()
            monitoring = !monitoring
        }) {
            Text(if (monitoring) "Arrêter la surveillance" else "Démarrer la surveillance")
        }

        Text("Durcissement", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(advice) { a ->
                Card(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val mark = if (a.available) "Disponible" else "Non disponible sur cet appareil"
                        Text("${a.title}  —  $mark",
                            style = MaterialTheme.typography.titleSmall)
                        Text(a.rationale, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
