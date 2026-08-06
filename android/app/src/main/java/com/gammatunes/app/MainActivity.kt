package com.gammatunes.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.gammatunes.app.model.Track
import com.gammatunes.app.player.PlayerState
import com.gammatunes.app.player.rememberPlayerState
import com.gammatunes.app.ui.screens.AlbumDetailScreen
import com.gammatunes.app.ui.screens.ArtistDetailScreen
import com.gammatunes.app.ui.screens.ArtistReleaseKind
import com.gammatunes.app.ui.screens.ArtistAlbumsGridScreen
import com.gammatunes.app.ui.screens.ArtistSongsScreen
import com.gammatunes.app.offline.OfflineRepository
import com.gammatunes.app.ui.screens.MoreScreen
import com.gammatunes.app.ui.screens.AccountScreen
import com.gammatunes.app.ui.screens.AppearanceScreen
import com.gammatunes.app.ui.screens.OfflineTracksScreen
import com.gammatunes.app.ui.screens.OfflineAlbumDetailScreen
import com.gammatunes.app.ui.screens.OfflineAlbumsScreen
import com.gammatunes.app.ui.screens.PlayerScreen
import com.gammatunes.app.ui.screens.SearchScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import com.gammatunes.app.ui.theme.DynamicAccent
import androidx.compose.runtime.LaunchedEffect
import com.gammatunes.app.ui.i18n.LocalLanguage
import com.gammatunes.app.ui.i18n.LocalStrings
import com.gammatunes.app.ui.i18n.LocaleRepository
import com.gammatunes.app.ui.i18n.stringsFor
import com.gammatunes.app.ui.theme.GammaTunesTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            GammaTunesTheme {
                App()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001,
                )
            }
        }
    }
}

private sealed class Screen(val route: String, val icon: ImageVector) {
    data object Search : Screen("search", Icons.Default.Search)
    data object Player : Screen("player", Icons.Default.MusicNote)
    data object More : Screen("more", Icons.Default.MoreHoriz)
}

private val bottomTabs = listOf(Screen.Search, Screen.Player, Screen.More)

@Composable
fun App() {
    val lang by LocaleRepository.language.collectAsState()
    val strings = stringsFor(lang)
    CompositionLocalProvider(
        LocalLanguage provides lang,
        LocalStrings provides strings,
    ) {
    AppContent()
    }
}

@Composable
private fun AppContent() {
    val strings = LocalStrings.current
    val navController = rememberNavController()
    val playerState = rememberPlayerState()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route


    LaunchedEffect(playerState.currentTrack?.thumbnail) {
        val thumb = playerState.currentTrack?.thumbnail
        if (thumb.isNullOrBlank()) {
            DynamicAccent.clear()
        } else {
            DynamicAccent.updateFromThumbnail(context, thumb)
        }
    }

    fun openPlayerTab() {
        navController.navigate(Screen.Player.route) {
            popUpTo(navController.graph.startDestinationId)
            launchSingleTop = true
        }
    }




    val onTrackClick: (Track, List<Track>) -> Unit = { track, queue ->
        playerState.play(track, queue)
        openPlayerTab()
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Search.route,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            composable(Screen.Search.route) {
                SearchScreen(
                    onArtistClick = { artist ->
                        val encodedId = URLEncoder.encode(artist.artistId, "UTF-8")
                        navController.navigate("artist/$encodedId")
                    },
                    onTrackClick = { track, queue -> onTrackClick(track, queue) },
                )
            }
            composable(
                route = "artist/{artistId}",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val encodedId = backStackEntry.arguments?.getString("artistId").orEmpty()
                val artistIdDecoded = URLDecoder.decode(encodedId, "UTF-8")
                ArtistDetailScreen(
                    artistId = artistIdDecoded,
                    onAlbumClick = { album ->
                        val encodedAlbumId = URLEncoder.encode(album.albumId, "UTF-8")
                        navController.navigate("album/$encodedAlbumId")
                    },
                    onTrackClick = onTrackClick,
                    onOpenPopular = {
                        navController.navigate("artist/$encodedId/songs")
                    },
                    onOpenAlbums = {
                        navController.navigate("artist/$encodedId/albums")
                    },
                    onOpenSingles = {
                        navController.navigate("artist/$encodedId/singles")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "artist/{artistId}/songs",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { entry ->
                val encodedId = entry.arguments?.getString("artistId").orEmpty()
                ArtistSongsScreen(
                    artistId = URLDecoder.decode(encodedId, "UTF-8"),
                    onTrackClick = onTrackClick,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "artist/{artistId}/albums",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { entry ->
                val encodedId = entry.arguments?.getString("artistId").orEmpty()
                ArtistAlbumsGridScreen(
                    artistId = URLDecoder.decode(encodedId, "UTF-8"),
                    kind = ArtistReleaseKind.ALBUMS,
                    onAlbumClick = { album ->
                        val encodedAlbumId = URLEncoder.encode(album.albumId, "UTF-8")
                        navController.navigate("album/$encodedAlbumId")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "artist/{artistId}/singles",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { entry ->
                val encodedId = entry.arguments?.getString("artistId").orEmpty()
                ArtistAlbumsGridScreen(
                    artistId = URLDecoder.decode(encodedId, "UTF-8"),
                    kind = ArtistReleaseKind.SINGLES,
                    onAlbumClick = { album ->
                        val encodedAlbumId = URLEncoder.encode(album.albumId, "UTF-8")
                        navController.navigate("album/$encodedAlbumId")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val encodedAlbumId = backStackEntry.arguments?.getString("albumId").orEmpty()
                AlbumDetailScreen(
                    albumId = URLDecoder.decode(encodedAlbumId, "UTF-8"),
                    onTrackClick = onTrackClick,
                    onBack = { navController.popBackStack() },
                )


            }
            composable(Screen.Player.route) {
                PlayerScreen(
                    player = playerState,
                    onArtistClick = { artistId ->
                        val encodedId = URLEncoder.encode(artistId, "UTF-8")
                        navController.navigate("artist/$encodedId")
                    },
                    onAlbumClick = { albumId ->
                        val encodedAlbumId = URLEncoder.encode(albumId, "UTF-8")
                        navController.navigate("album/$encodedAlbumId")
                    },
                )
            }
            composable(Screen.More.route) {
                MoreScreen(
                    onOpenAccount = { navController.navigate("more/account") },
                    onOpenAppearance = { navController.navigate("more/appearance") },
                    onOpenOfflineTracks = { navController.navigate("more/offline_tracks") },
                    onOpenOfflineAlbums = { navController.navigate("more/offline_albums") },
                )
            }
            composable("more/account") {
                AccountScreen(
                    onTrackClick = onTrackClick,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("more/appearance") {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
            composable("more/offline_tracks") {
                OfflineTracksScreen(
                    onTrackClick = onTrackClick,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("more/offline_albums") {
                OfflineAlbumsScreen(
                    onAlbumClick = { albumId ->

                        val encoded = URLEncoder.encode(albumId, "UTF-8")
                        navController.navigate("more/offline_album/$encoded")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "more/offline_album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) { entry ->
                val encoded = entry.arguments?.getString("albumId").orEmpty()
                OfflineAlbumDetailScreen(
                    albumId = URLDecoder.decode(encoded, "UTF-8"),
                    onTrackClick = onTrackClick,
                    onBack = { navController.popBackStack() },
                )
            }
        }


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
        ) {
            val playingTrack = playerState.currentTrack
            if (playingTrack != null && currentRoute != Screen.Player.route) {
                MiniPlayerBar(
                    track = playingTrack,
                    isPlaying = playerState.isPlaying,
                    onOpenPlayer = { openPlayerTab() },
                    onTogglePlay = { playerState.togglePlayPause() },
                )
            }
            BottomBar(navController = navController, currentRoute = currentRoute)
        }
    }
}

@Composable
private fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = track.title,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) LocalStrings.current.pause else LocalStrings.current.play,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    val strings = LocalStrings.current
    fun labelFor(screen: Screen): String = when (screen) {
        Screen.Search -> strings.tabSearch
        Screen.Player -> strings.tabPlayer
        Screen.More -> strings.tabMore
    }
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        bottomTabs.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route ||
                        (screen.route == Screen.More.route && currentRoute?.startsWith("more/") == true),
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = labelFor(screen),
                    )
                },
                label = { Text(labelFor(screen)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
