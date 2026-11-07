package app.gobolt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import app.gobolt.home.HomeScreen
import app.gobolt.home.HomeViewModel
import app.gobolt.ui.GoboltTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoboltTheme {
                HomeScreen(viewModel)
            }
        }
    }
}
