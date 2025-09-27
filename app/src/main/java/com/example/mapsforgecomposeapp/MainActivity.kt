/**
package com.example.mapsforgecomposeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Disattiva il "fit" delle system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ✅ Controller per gestire le barre
        val controller = WindowInsetsControllerCompat(window, window.decorView).apply {
            // Le barre riappaiono solo con swipe dal bordo
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Nasconde subito status bar + navigation bar
            hide(WindowInsetsCompat.Type.systemBars())
        }



        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MapsforgeMapComposable(context = this,)
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MapScreenPreview() {
    // In preview non hai un vero Context → workaround con LocalContext
    val context = LocalContext.current
    MapsforgeMapComposable(context = context,)
}

**/ //fine backup - pulsante centra in posizione, pulsanti zomm e bordo

package com.example.mapsforgecomposeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Disattiva il "fit" delle system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ✅ Controller per gestire le barre
        val controller = WindowInsetsControllerCompat(window, window.decorView).apply {
            // Le barre riappaiono solo con swipe dal bordo
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Nasconde subito status bar + navigation bar
            hide(WindowInsetsCompat.Type.systemBars())
        }



        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MapsforgeMapComposable(context = this)
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MapScreenPreview() {
    // In preview non hai un vero Context → workaround con LocalContext
    val context = LocalContext.current
    MapsforgeMapComposable(context = context)
}
