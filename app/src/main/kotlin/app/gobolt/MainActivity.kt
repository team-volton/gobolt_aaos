package app.gobolt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import app.gobolt.ui.GoboltTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GoboltViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoboltTheme {
                GoboltApp(viewModel)
            }
        }
    }
}
