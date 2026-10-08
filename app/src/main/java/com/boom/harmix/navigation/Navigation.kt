package com.boom.harmix.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.ui.screens.AccountScreen
import com.boom.harmix.ui.screens.GuestLockedScreen
import com.boom.harmix.ui.screens.HomeScreen
import com.boom.harmix.ui.screens.LibraryScreen
import com.boom.harmix.ui.screens.ListeningHistoryScreen
import com.boom.harmix.ui.screens.PlaylistDetailScreen
import com.boom.harmix.ui.screens.SearchScreen
import com.boom.harmix.ui.screens.SettingsScreen

sealed class HarmixDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : HarmixDestination("home", "Home", Icons.Filled.Home)
    data object Search : HarmixDestination("search", "Search", Icons.Filled.Search)
    data object Library : HarmixDestination("library", "Library", Icons.Filled.List)
    data object Account : HarmixDestination("account", "Account", Icons.Filled.AccountCircle)
}

@Suppress("UNUSED_PARAMETER")
fun bottomNavItemsFor(isGuest: Boolean): List<HarmixDestination> =
    if (isGuest) {
        listOf(HarmixDestination.Home, HarmixDestination.Search, HarmixDestination.Account)
    } else {
        listOf(
            HarmixDestination.Home,
            HarmixDestination.Search,
            HarmixDestination.Library,
            HarmixDestination.Account
        )
    }

@Composable
fun HarmixNavHost(
    navController: NavHostController,
    playTrack: (StreamItem) -> Unit,
    onPlayQueue: (List<StreamItem>, Int) -> Unit,
    isGuest: Boolean,
    username: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPlayNext: (StreamItem) -> Unit,
    onAddToQueue: (StreamItem) -> Unit,
    onAddToPlaylistRequest: (StreamItem) -> Unit,
    likedUrls: Set<String>,
    onToggleLike: (StreamItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenListeningHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = HarmixDestination.Home.route,
        modifier = modifier
    ) {
        composable(HarmixDestination.Home.route) {
            HomeScreen(
                isGuest = isGuest,
                username = username,
                onItemClick = playTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylistRequest = onAddToPlaylistRequest,
                likedUrls = likedUrls,
                onToggleLike = onToggleLike
            )
        }
        composable(HarmixDestination.Search.route) {
            SearchScreen(
                isGuest = isGuest,
                onItemClick = playTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylistRequest = onAddToPlaylistRequest,
                likedUrls = likedUrls,
                onToggleLike = onToggleLike
            )
        }
        composable(HarmixDestination.Library.route) {
            if (isGuest) GuestLockedScreen()
            else {
                LibraryScreen(
                    onPlayQueue = onPlayQueue,
                    onOpenPlaylist = { playlistId -> navController.navigate("playlist/$playlistId") }
                )
            }
        }
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) {
            if (isGuest) GuestLockedScreen()
            else {
                PlaylistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPlayQueue = onPlayQueue,
                    onAddToQueue = onAddToQueue
                )
            }
        }
        composable(HarmixDestination.Account.route) {
            AccountScreen(
                onOpenSettings = onOpenSettings,
                onOpenListeningHistory = onOpenListeningHistory
            )
        }
        composable("listening-history") {
            if (isGuest) GuestLockedScreen()
            else {
                ListeningHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onItemClick = playTrack,
                    likedUrls = likedUrls,
                    onToggleLike = onToggleLike
                )
            }
        }
        composable("settings") {
            if (isGuest) GuestLockedScreen()
            else SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
