package it.fourSTL.PositionMarker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        FirebaseConfig.initialize(this)
        enableEdgeToEdge()

        setContent {
            var showSplashScreen by remember { mutableStateOf(true) }

            LaunchedEffect(key1 = true) {
                delay(3000) // Mostra lo splash screen per 3 secondi
                showSplashScreen = false
            }

            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (showSplashScreen) {
                        SplashScreenView()
                    } else {
                        fourSTLPositionMarkerComposable(context = this@MainActivity)
                    }
                }
            }
        }
    }
}
