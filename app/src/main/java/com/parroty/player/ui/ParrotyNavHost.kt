package com.parroty.player.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.parroty.player.ui.browse.BrowseScreen
import com.parroty.player.ui.library.LibraryScreen
import com.parroty.player.ui.local.LocalSetupScreen
import com.parroty.player.ui.pair.PairScreen
import com.parroty.player.ui.player.PlayerScreen

object Routes {
    const val LIBRARY = "library"
    const val BROWSE = "browse"
    const val LOCAL = "local"
    const val PAIR = "pair/{videoId}/{folderId}"
    const val PLAYER = "player/{fileId}"

    fun pair(videoId: String, folderId: String) = "pair/$videoId/$folderId"

    /**
     * A local book's id is its content:// uri, which is full of slashes and
     * colons and would otherwise be read as extra path segments. Drive ids are
     * unaffected by the escaping.
     */
    fun player(fileId: String) = "player/" + Uri.encode(fileId)
}

@Composable
fun ParrotyNavHost(onAuthExpired: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBrowseDrive = { navController.navigate(Routes.BROWSE) },
                onOpenLocal = { navController.navigate(Routes.LOCAL) },
                onOpenBook = { fileId -> navController.navigate(Routes.player(fileId)) },
                onAuthExpired = onAuthExpired
            )
        }

        composable(Routes.BROWSE) {
            BrowseScreen(
                onBack = { navController.popBackStack() },
                onPickVideo = { video, folderId ->
                    navController.navigate(Routes.pair(video.id, folderId))
                },
                onAuthExpired = onAuthExpired
            )
        }

        composable(Routes.LOCAL) {
            LocalSetupScreen(
                onBack = { navController.popBackStack() },
                onPlay = { fileId ->
                    navController.navigate(Routes.player(fileId)) {
                        popUpTo(Routes.LIBRARY)
                    }
                }
            )
        }

        composable(
            route = Routes.PAIR,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("folderId") { type = NavType.StringType }
            )
        ) { entry ->
            val videoId = entry.arguments?.getString("videoId").orEmpty()
            val folderId = entry.arguments?.getString("folderId").orEmpty()
            PairScreen(
                videoId = videoId,
                folderId = folderId,
                onBack = { navController.popBackStack() },
                onPlay = { fileId ->
                    navController.navigate(Routes.player(fileId)) {
                        // Coming back from the player should land on the library,
                        // not walk back through the pairing step.
                        popUpTo(Routes.LIBRARY)
                    }
                },
                onAuthExpired = onAuthExpired
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("fileId") { type = NavType.StringType })
        ) { entry ->
            // Navigation unescapes path arguments on the way out. Decoding a
            // second time is a no-op for ids without percent escapes, and cheap
            // insurance if it ever stops.
            val fileId = Uri.decode(entry.arguments?.getString("fileId").orEmpty())
            PlayerScreen(
                fileId = fileId,
                onBack = { navController.popBackStack() },
                onAuthExpired = onAuthExpired
            )
        }
    }
}
