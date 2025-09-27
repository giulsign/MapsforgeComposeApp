package com.example.mapsforgecomposeapp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview


@Composable
fun LocationsTableScreen(
    context: Context,
    onBack: () -> Unit
) {
    val locations = remember { readLocationsFromJson(context) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Punti salvati", style = MaterialTheme.typography.titleLarge)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(locations) { loc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("${loc.id}", modifier = Modifier.weight(1f))
                    Text("${loc.latitude}", modifier = Modifier.weight(1f))
                    Text("${loc.longitude}", modifier = Modifier.weight(1f))
                    Text("${loc.altitude}", modifier = Modifier.weight(1f))
                    Text(loc.date, modifier = Modifier.weight(1f))
                    Text(loc.hour, modifier = Modifier.weight(1f))
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Chiudi")
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LocationsTableScreenPreview() {
    // In preview non hai un vero Context → workaround con LocalContext
    val context = LocalContext.current
    LocationsTableScreen(context = context, onBack = {})
}
