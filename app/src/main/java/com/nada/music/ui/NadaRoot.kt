package com.nada.music.ui

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.nada.music.ui.home.HomeScreen
import com.nada.music.ui.library.LibraryScreen
import com.nada.music.ui.library.PlaylistScreen
import com.nada.music.ui.player.PlayerScreen
import com.nada.music.ui.search.SearchScreen

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYLIST = "playlist/{id}"
    const val PLAYER = "player"
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
    val onPlayer = route == Routes.PLAYER

    LaunchedEffect(openPlayerRequest.value, currentSong != null) {
        if (openPlayerRequest.value && currentSong != null) {
            openPlayerRequest.value = false
            if (nav.currentDestination?.route != Routes.PLAYER) nav.navigate(Routes.PLAYER)
        }
    }

    Scaffold(
        bottomBar = {
            if (!onPlayer) {
                Column {
                    MiniPlayer(onOpen = { nav.navigate(Routes.PLAYER) })
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val listPadding = PaddingValues(bottom = padding.calculateBottomPadding())
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
                LibraryScreen(onOpenPlaylist = { nav.navigate(Routes.playlist(it)) }, contentPadding = listPadding)
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
                exitTransition = { slideOutVertically { it } },
                popEnterTransition = { slideInVertically { it } },
                popExitTransition = { slideOutVertically { it } },
            ) {
                PlayerScreen(onClose = { nav.popBackStack() })
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
