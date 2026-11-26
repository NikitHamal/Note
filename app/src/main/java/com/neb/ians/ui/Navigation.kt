package com.neb.ians.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neb.ians.ui.screens.about.AboutScreen
import com.neb.ians.ui.screens.notes.NoteEditorScreen
import com.neb.ians.ui.screens.notes.NotesScreen
import com.neb.ians.ui.screens.notes.VoiceModelsScreen
import com.neb.ians.ui.screens.splash.SplashScreen

@Composable
fun NoteNavHost(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "splash",
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(150)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable("splash") {
            SplashScreen(
                onFinished = {
                    navController.navigate("notes") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }
        composable("notes") {
            NotesScreen(
                onOpenNote = { id -> navController.navigate("note/$id?dictate=false") },
                onNewNote = { dictate -> navController.navigate("note/new?dictate=$dictate") },
                onOpenVoice = { navController.navigate("voice") },
                onOpenAbout = { navController.navigate("about") },
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
            )
        }
        composable(
            route = "note/{noteId}?dictate={dictate}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("dictate") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            NoteEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenNote = { id ->
                    navController.navigate("note/$id?dictate=false") {
                        popUpTo("note/{noteId}?dictate={dictate}") { inclusive = true }
                    }
                },
                onOpenVoice = { navController.navigate("voice") },
            )
        }
        composable("voice") {
            VoiceModelsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("about") {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenVoice = { navController.navigate("voice") },
            )
        }
    }
}
