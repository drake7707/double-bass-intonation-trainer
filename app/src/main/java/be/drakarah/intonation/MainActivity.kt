package be.drakarah.intonation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import be.drakarah.intonation.ui.AppNav
import be.drakarah.intonation.ui.theme.BassPitchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BassPitchTheme {
                AppNav()
            }
        }
    }
}
