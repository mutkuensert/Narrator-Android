package com.mutkuensert.seslendirmen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mutkuensert.seslendirmen.presentation.ttstest.TtsTestRoute
import com.mutkuensert.seslendirmen.presentation.ttstest.TtsTestViewModel
import com.mutkuensert.seslendirmen.ui.theme.SeslendirmenTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: TtsTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeslendirmenTheme {
                TtsTestRoute(viewModel = viewModel)
            }
        }
    }
}
