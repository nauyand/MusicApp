package com.nada.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nada.music.ui.components.MiniPlayer
import com.nada.music.ui.effects.EffectsScreen
import com.nada.music.ui.glass.AmbientBackground
import com.nada.music.ui.glass.LocalHazeState
import com.nada.music.ui.glass.glass
import com.nada.music.ui.glass.rememberArtworkColors
import com.nada.music.ui.home.HomeScreen
import com.nada.music.ui.library.LibraryScreen
import com.nada.music.ui.library.PlaylistScreen
import com.nada.music.ui.player.PlayerScreen
import com.nada.music.ui.search.SearchScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYLIST = "playlist/{id}"
    const val PLAYER = "player"
    const val EFFECTS = "effects"
    fun playlist(id: Long) = "playlist/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Beranda", Icons.Rounded.Home),
    Tab(Routes.SEARCH, "Cari", Icons.Rounded.Search),
    Tab(Routes.LIBRARY, "Pustaka", Icons.Rounded.LibraryMusic),
)

@Composable
fun NadaRoot(openPlayerRequest: MutableState<Boolean>) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val player = LocalPlayer.current
    val currentSong by player.currentSong.collectAsState()
    val fullScreen = route == Routes.PLAYER || route == Routes.EFFECTS
    val hazeState = remember { HazeState() }
    val artworkColors = rememberArtworkColors(currentSong?.thumbnailUrl)
    val density = LocalDensity.current
    var dockHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(openPlayerRequest.value, currentSong != null) {
        if (openPlayerRequest.value && currentSong != null) {
            openPlayerRequest.value = false
            if (nav.currentDestination?.route != Routes.PLAYER) nav.navigate(Routes.PLAYER)
        }
    }

    // Daftar lagu bisa digulir sampai ke bawah dock, tapi item terakhir tidak tertutup.
    val listPadding = PaddingValues(bottom = with(density) { dockHeightPx.toDp() } + 8.dp)

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().haze(hazeState)) {
                AmbientBackground(artworkColors)
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(onOpenSearch = { nav.switchTab(Routes.SEARCH) }, contentPadding = listPadding)
                    }
                    composable(Routes.SEARCH) { SearchScreen(contentPadding = listPadding) }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                            contentPadding = listPadding,
                        )
                    }
                    composable(
                        Routes.PLAYLIST,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        PlaylistScreen(
                            playlistId = entry.arguments?.getLong("id") ?: return@composable,
                            onBack = { nav.popBackStack() },
                            contentPadding = listPadding,
                        )
                    }
                    composable(
                        Routes.PLAYER,
                        enterTransition = { slideInVertically { it } },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { slideOutVertically { it } },
                    ) {
                        PlayerScreen(
                            colors = artworkColors,
                            onClose = { nav.popBackStack() },
                            onOpenEffects = { nav.navigate(Routes.EFFECTS) },
                        )
                    }
                    composable(
                        Routes.EFFECTS,
                        enterTransition = { slideInVertically { it } },
                        popExitTransition = { slideOutVertically { it } },
                    ) {
                        EffectsScreen(colors = artworkColors, onClose = { nav.popBackStack() })
                    }
                }
            }

            AnimatedVisibility(
                visible = !fullScreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onSizeChanged { dockHeightPx = it.height },
                ) {
                    MiniPlayer(onOpen = { nav.navigate(Routes.PLAYER) })
                    if (currentSong != null) Spacer(Modifier.height(8.dp))
                    GlassTabBar(
                        selectedRoute = tabs.firstOrNull { tab ->
                            backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        }?.route ?: if (route == Routes.PLAYLIST) Routes.LIBRARY else Routes.HOME,
                        onSelect = { nav.switchTab(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassTabBar(selectedRoute: String, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .glass(shape = shape, hazeState = LocalHazeState.current)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == selectedRoute
            val alpha by animateFloatAsState(if (selected) 1f else 0.6f, label = "tabAlpha")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (selected) Modifier.glass(shape = RoundedCornerShape(26.dp), borderWidth = 0.5.dp)
                        else Modifier,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab.route) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha)) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
