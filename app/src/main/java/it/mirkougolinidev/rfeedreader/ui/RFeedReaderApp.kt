package it.mirkougolinidev.rfeedreader.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.common.UserPreferences
import androidx.media3.common.PlaybackParameters
import it.mirkougolinidev.rfeedreader.ui.player.formatTime
import it.mirkougolinidev.rfeedreader.ui.player.playDownloadedEpisode
import it.mirkougolinidev.rfeedreader.ui.player.rememberMediaController
import it.mirkougolinidev.rfeedreader.ui.player.rememberPlaybackSnapshotState
import it.mirkougolinidev.rfeedreader.ui.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class Destination(val route: String, val label: String, val symbol: String)

@Composable
fun RFeedReaderApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    val repository = (context.applicationContext as RFeedReaderApplication).repository
    val controller = rememberMediaController()
    // Letto solo dove serve (barra del mini player, schermata player, gestori di eventi).
    val playbackState = rememberPlaybackSnapshotState(controller)
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerRemaining by remember { mutableIntStateOf(0) }
    val playableEpisodes by repository.playableEpisodes.collectAsStateWithLifecycle(emptyList())
    val podcasts by repository.podcasts.collectAsStateWithLifecycle(emptyList())
    val podcastTitles = podcasts.associate { it.id to it.title }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val destinations = listOf(
        Destination("home", "Home", "⌂"),
        Destination("podcasts", "Podcast", "◉"),
        Destination("downloads", "Download", "↓"),
        Destination("settings", "Impostazioni", "⚙")
    )

    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            var remaining = sleepTimerMinutes
            while (remaining > 0 && isActive) {
                delay(60_000)
                remaining -= 1
                sleepTimerRemaining = remaining
            }
            if (isActive && remaining == 0) controller?.pause()
            sleepTimerMinutes = 0
            sleepTimerRemaining = 0
        }
    }

    fun openEpisode(episode: it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity) {
        controller?.setPlaybackParameters(PlaybackParameters(UserPreferences.get(context).getFloat(UserPreferences.PLAYBACK_SPEED, 1f)))
        playDownloadedEpisode(controller, playableEpisodes, episode)
        navController.navigate("player") { launchSingleTop = true }
    }

    fun removeEpisode(episode: it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity) {
        controller?.let { player ->
            if (playbackState.value.episodeId == episode.id) {
                player.stop()
                player.clearMediaItems()
            } else {
                // Toglie l'episodio anche dalla coda, altrimenti il player ci arriverebbe e fallirebbe.
                (player.mediaItemCount - 1 downTo 0)
                    .filter { player.getMediaItemAt(it).mediaId == episode.id }
                    .forEach { player.removeMediaItem(it) }
            }
        }
        val scheduler = (context.applicationContext as RFeedReaderApplication).downloadScheduler
        appScope.launch { runCatching { scheduler.removeEpisode(episode.id) } }
    }

    fun beforeRemovePodcast(podcastId: String) {
        // L'episodio in riproduzione è per forza scaricato e verificato, quindi sta in playableEpisodes.
        if (playableEpisodes.any { it.id == playbackState.value.episodeId && it.podcastId == podcastId }) {
            controller?.stop()
            controller?.clearMediaItems()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                val playback = playbackState.value
                if (playback.episodeId != null && currentRoute != "player") {
                    MiniPlayer(
                        title = playback.title,
                        isPlaying = playback.isPlaying,
                        position = playback.positionMs,
                        duration = playback.durationMs,
                        onOpen = { navController.navigate("player") { launchSingleTop = true } },
                        onToggle = { if (playback.isPlaying) controller?.pause() else controller?.play() },
                        onBack = { controller?.seekTo((playback.positionMs - 30_000).coerceAtLeast(0)) },
                        onForward = { controller?.seekTo((playback.positionMs + 30_000).coerceAtMost(playback.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)) }
                    )
                }
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentRoute == destination.route ||
                            (destination.route == "podcasts" && currentRoute == "podcast/{podcastId}")
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Text(destination.symbol, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = destination.label })
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Niente dissolvenza tra le schermate: sul Fire 7 disegnare due schermate in trasparenza
        // per 700 ms costava oltre mezzo secondo di frame persi a ogni cambio di scheda.
        NavHost(
            navController = navController, startDestination = "home", modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }
        ) {
            composable("home") {
                HomeScreen(
                    episodes = playableEpisodes,
                    podcastTitles = podcastTitles,
                    onPlay = ::openEpisode,
                    onBrowsePodcasts = { navController.navigate("podcasts") { launchSingleTop = true } }
                )
            }
            composable("podcasts") {
                PodcastsScreen(onPodcastClick = { podcast ->
                    navController.navigate("podcast/${podcast.id}")
                })
            }
            composable("podcast/{podcastId}", arguments = listOf(navArgument("podcastId") { type = NavType.StringType })) { entry ->
                PodcastDetailScreen(
                    podcastId = entry.arguments?.getString("podcastId").orEmpty(),
                    onPlay = ::openEpisode,
                    onRemoveEpisode = ::removeEpisode,
                    onBeforeRemove = ::beforeRemovePodcast,
                    onRemoved = { navController.popBackStack() }
                )
            }
            composable("downloads") { DownloadsScreen(onPlay = ::openEpisode, onRemoveEpisode = ::removeEpisode) }
            composable("settings") { SettingsScreen() }
            composable("player") {
                PlayerScreen(controller, playbackState.value, playableEpisodes, podcastTitles, sleepTimerRemaining) {
                    sleepTimerMinutes = it
                    sleepTimerRemaining = it
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Text(title.ifBlank { "Episodio" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatTime(position)} / ${formatTime(duration)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text("−30") }
            Button(onClick = onToggle, modifier = Modifier.heightIn(min = 56.dp)) { Text(if (isPlaying) "Pausa" else "Play") }
            Button(onClick = onForward, modifier = Modifier.heightIn(min = 56.dp)) { Text("+30") }
        }
    }
}
