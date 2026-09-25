package com.mutkuensert.seslendirmen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mutkuensert.seslendirmen.presentation.reader.ReaderRoute
import com.mutkuensert.seslendirmen.presentation.reader.ReaderViewModel
import com.mutkuensert.seslendirmen.ui.theme.SeslendirmenTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeslendirmenTheme {
                ReaderRoute(viewModel = viewModel)
            }
        }
    }
}
