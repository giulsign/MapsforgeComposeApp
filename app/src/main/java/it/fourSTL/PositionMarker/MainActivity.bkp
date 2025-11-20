package it.fourSTL.PositionMarker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // Abilita la visualizzazione edge-to-edge
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    fourSTLPositionMarkerComposable(context = this)
                }
            }
        }
    }
}
/*
@Composable
fun MapsforgeMapComposable() {
    // Aggiunge padding per rispettare le barre di sistema
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Text(
            text = "Contenuto dell’app",
            fontSize = 20.sp,
            color = Color.Black,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}*/