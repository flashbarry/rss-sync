package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.ArticleListScreen
import com.example.ui.screens.ArticleReaderScreen
import com.example.ui.screens.ServerLoginScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            is AppScreen.ServerLogin -> {
                ServerLoginScreen(viewModel = viewModel)
            }
            is AppScreen.ArticleList -> {
                ArticleListScreen(viewModel = viewModel)
            }
            is AppScreen.ArticleReader -> {
                ArticleReaderScreen(
                    articleId = screen.articleId,
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }
}
