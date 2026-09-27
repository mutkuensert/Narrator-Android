package com.mutkuensert.seslendirmen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.mutkuensert.seslendirmen.core.ui.theme.SeslendirmenTheme
import com.mutkuensert.seslendirmen.navigation.AppNavHost
import com.mutkuensert.seslendirmen.navigation.NavigationCommand
import com.mutkuensert.seslendirmen.navigation.Navigator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var navigator: Navigator

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            SeslendirmenTheme {
                val navController = rememberNavController()
                LaunchedEffect(navController, navigator) {
                    navigator.commands.collect { command ->
                        when (command) {
                            is NavigationCommand.ToRoute -> {
                                navController.navigate(command.route)
                            }

                            NavigationCommand.Back -> navController.popBackStack()
                        }
                    }
                }
                AppNavHost(navController)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
