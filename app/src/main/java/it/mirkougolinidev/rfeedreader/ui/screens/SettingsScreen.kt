package it.mirkougolinidev.rfeedreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.common.UserPreferences
import it.mirkougolinidev.rfeedreader.core.network.AutomaticRefreshScheduler
import it.mirkougolinidev.rfeedreader.core.network.OpmlDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { UserPreferences.get(context) }
    val repository = (context.applicationContext as RFeedReaderApplication).repository
    val podcasts by repository.podcasts.collectAsStateWithLifecycle(emptyList())
    val refreshScheduler = remember { AutomaticRefreshScheduler(context) }
    val scope = rememberCoroutineScope()
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean(UserPreferences.WIFI_ONLY, false)) }
    var backSkip by remember { mutableStateOf(prefs.getInt(UserPreferences.SKIP_BACK, 30)) }
    var forwardSkip by remember { mutableStateOf(prefs.getInt(UserPreferences.SKIP_FORWARD, 30)) }
    var speed by remember { mutableStateOf(prefs.getFloat(UserPreferences.PLAYBACK_SPEED, 1f)) }
    var automaticRefresh by remember { mutableStateOf(prefs.getBoolean(UserPreferences.AUTOMATIC_REFRESH, false)) }
    var opmlStatus by remember { mutableStateOf<String?>(null) }
    var showOpmlStatus by remember { mutableStateOf(false) }

    val importOpml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                val urls = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(OpmlDocument::read)
                        ?: error("Impossibile aprire il file selezionato.")
                }
                var added = 0
                var failed = 0
                urls.forEach { url ->
                    if (runCatching { repository.addFeed(url) }.isSuccess) added++ else failed++
                }
                "$added feed importati${if (failed > 0) " · $failed non riusciti" else ""}"
            }
            opmlStatus = result.getOrElse { it.message ?: "Importazione OPML non riuscita." }
            showOpmlStatus = true
        }
    }
    val exportOpml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { OpmlDocument.write(podcasts, it) }
                        ?: error("Impossibile scrivere nel file selezionato.")
                }
                "${podcasts.size} feed esportati in OPML."
            }
            opmlStatus = result.getOrElse { it.message ?: "Esportazione OPML non riuscita." }
            showOpmlStatus = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenHeader("Impostazioni", "Un ascolto su misura")
        SwitchPreference("Solo rete non a consumo", "Le code nuove attendono una rete Wi-Fi o un'altra rete non a consumo", wifiOnly) {
            wifiOnly = it
            prefs.edit().putBoolean(UserPreferences.WIFI_ONLY, it).apply()
        }
        SwitchPreference("Aggiorna i feed automaticamente", "Controlla i nuovi episodi ogni 12 ore quando c'è rete", automaticRefresh) {
            automaticRefresh = it
            prefs.edit().putBoolean(UserPreferences.AUTOMATIC_REFRESH, it).apply()
            if (it) refreshScheduler.enable() else refreshScheduler.disable()
        }
        ChoicePreference("Salto indietro", "${backSkip} secondi", listOf("15", "30", "45", "60"), backSkip.toString()) {
            backSkip = it.toInt(); prefs.edit().putInt(UserPreferences.SKIP_BACK, backSkip).apply()
        }
        ChoicePreference("Salto avanti", "${forwardSkip} secondi", listOf("15", "30", "45", "60"), forwardSkip.toString()) {
            forwardSkip = it.toInt(); prefs.edit().putInt(UserPreferences.SKIP_FORWARD, forwardSkip).apply()
        }
        ChoicePreference("Velocità predefinita", "%.2gx".format(speed), listOf("0.8", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5"), "%.2g".format(speed)) {
            speed = it.toFloat(); prefs.edit().putFloat(UserPreferences.PLAYBACK_SPEED, speed).apply()
        }
        PreferenceCard("Aspetto", "Tema scuro ad alto contrasto. La dimensione del testo segue le impostazioni di accessibilità Android.")
        PreferenceCard("Spazio", "I download restano nell'archivio privato dell'app. Puoi rimuovere singoli episodi dalla sezione Download.")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sottoscrizioni OPML", style = MaterialTheme.typography.titleLarge)
            Text("Trasferisci i tuoi feed tra app o dispositivi.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { importOpml.launch(arrayOf("application/xml", "text/xml", "application/octet-stream")) },
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("Importa OPML") }
                Button(enabled = podcasts.isNotEmpty(), onClick = { exportOpml.launch("rfeedreader.opml") },
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("Esporta OPML") }
            }
        }
        opmlStatus?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
        PreferenceCard("Privacy", "Nessun account, cloud o servizio di analisi è configurato.")
        Text("RFeedReader · versione 0.1.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showOpmlStatus) AlertDialog(
        onDismissRequest = { showOpmlStatus = false },
        title = { Text("OPML") }, text = { Text(opmlStatus.orEmpty()) },
        confirmButton = { TextButton(onClick = { showOpmlStatus = false }) { Text("OK") } }
    )
}

@Composable
private fun SwitchPreference(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ChoicePreference(title: String, current: String, choices: List<String>, selected: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 56.dp)) { Text(current) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    choices.forEach { option ->
                        DropdownMenuItem(text = { Text(if (title == "Velocità predefinita") "$option×" else "$option s") },
                            onClick = { onChange(option); expanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceCard(title: String, detail: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

