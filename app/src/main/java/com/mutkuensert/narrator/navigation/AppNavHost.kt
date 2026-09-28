package com.mutkuensert.narrator.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mutkuensert.narrator.feature.reader.presentation.reader.ReaderScreen
import com.mutkuensert.narrator.feature.reader.presentation.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = ReaderRoute,
    ) {
        composable<ReaderRoute> {
            ReaderScreen()
        }
        composable<SettingsRoute> {
            SettingsScreen()
        }
    }
}

