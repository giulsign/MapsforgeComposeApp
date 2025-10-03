package it.fourSTL.PositionMarker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

// Enum per i livelli di navigazione
private enum class Level { CATEGORIA, SOTTOCATEGORIA, METADATI }

// Data class per le righe
data class SelectRow(val id: String, val title: String, val note: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    // Stati
    var currentLevel by remember { mutableStateOf(Level.CATEGORIA) }
    var selectedCategoria by remember { mutableStateOf<SelectRow?>(null) }
    var selectedSottocategoria by remember { mutableStateOf<SelectRow?>(null) }
    var selectedMetadataIds by remember { mutableStateOf(linkedSetOf<String>()) }

    // Dati demo (puoi sostituirli con dati reali)
    val categorie = remember {
        listOf(
            SelectRow("cat1", "Categoria 1", "descrizione 1"),
            SelectRow("cat2", "Categoria 2", "descrizione 2"),
            SelectRow("cat3", "Categoria 3", "descrizione 3"),
            SelectRow("cat4", "Categoria 4", "descrizione 4"),
            SelectRow("cat5", "Categoria 5", "descrizione 5")
        )
    }

    val sottocategorieMap = remember {
        mapOf(
            "cat1" to (1..5).map { SelectRow("cat1_sub$it", "Opzione $it", "tipo $it") },
            "cat2" to (1..5).map { SelectRow("cat2_sub$it", "Opzione $it", "tipo $it") },
            "cat3" to (1..5).map { SelectRow("cat3_sub$it", "Opzione $it", "tipo $it") },
            "cat4" to (1..5).map { SelectRow("cat4_sub$it", "Opzione $it", "tipo $it") },
            "cat5" to (1..5).map { SelectRow("cat5_sub$it", "Opzione $it", "tipo $it") }
        )
    }

    val metadataMap = remember {
        val map = mutableMapOf<String, List<SelectRow>>()
        for ((_, list) in sottocategorieMap) {
            for (sub in list) {
                val rows = (1..5).map { i ->
                    SelectRow("${sub.id}_md$i", "Metadato $i", "valore $i")
                }
                map[sub.id] = rows
            }
        }
        map
    }

    // Funzione per determinare il titolo
    val title = when (currentLevel) {
        Level.CATEGORIA -> "Seleziona Categoria"
        Level.SOTTOCATEGORIA -> "Sottocategorie di ${selectedCategoria?.title ?: ""}"
        Level.METADATI -> "Metadati — ${selectedCategoria?.title ?: ""} / ${selectedSottocategoria?.title ?: ""}"
    }

    // Funzione per determinare i dati da mostrare
    val currentRows = when (currentLevel) {
        Level.CATEGORIA -> categorie
        Level.SOTTOCATEGORIA -> sottocategorieMap[selectedCategoria?.id] ?: emptyList()
        Level.METADATI -> metadataMap[selectedSottocategoria?.id] ?: emptyList()
    }

    val isSelectable = currentLevel == Level.METADATI

    // Funzione back
    val onBackPressed = {
        when (currentLevel) {
            Level.CATEGORIA -> onDismiss()
            Level.SOTTOCATEGORIA -> {
                currentLevel = Level.CATEGORIA
                selectedCategoria = null
            }
            Level.METADATI -> {
                currentLevel = Level.SOTTOCATEGORIA
                selectedSottocategoria = null
            }
        }
    }

    // Funzione click su riga
    val onRowClick: (SelectRow) -> Unit = { row ->
        when (currentLevel) {
            Level.CATEGORIA -> {
                selectedCategoria = row
                currentLevel = Level.SOTTOCATEGORIA
            }
            Level.SOTTOCATEGORIA -> {
                selectedSottocategoria = row
                currentLevel = Level.METADATI
            }
            Level.METADATI -> {
                if (selectedMetadataIds.contains(row.id)) {
                    selectedMetadataIds.remove(row.id)
                } else {
                    selectedMetadataIds.add(row.id)
                }
                selectedMetadataIds = linkedSetOf<String>().apply { addAll(selectedMetadataIds) }
            }
        }
    }

    // Funzione salva
    val onSaveClicked = {
        val resultArray = JSONArray()
        val idToRowMap = metadataMap.values.flatten().associateBy { it.id }

        for (id in selectedMetadataIds) {
            idToRowMap[id]?.let { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("title", r.title)
                obj.put("note", r.note)
                obj.put("categoria", selectedCategoria?.title ?: "")
                obj.put("sottocategoria", selectedSottocategoria?.title ?: "")
                resultArray.put(obj)
            }
        }

        onSave(resultArray.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, "Indietro")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBackPressed) {
                    Text("Indietro")
                }

                TextButton(onClick = onDismiss) {
                    Text("Annulla")
                }

                if (currentLevel == Level.METADATI) {
                    Button(onClick = onSaveClicked) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Salva")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(currentRows) { row ->
                SelectionRowItem(
                    row = row,
                    isSelected = selectedMetadataIds.contains(row.id),
                    isSelectable = isSelectable,
                    onClick = { onRowClick(row) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SelectionRowItem(
    row: SelectRow,
    isSelected: Boolean,
    isSelectable: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelectable && isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (row.note.isNotEmpty()) {
                Text(
                    text = row.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isSelectable && isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selezionato",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}