package it.fourSTL.PositionMarker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    onSave: (String) -> Unit,
    startFile: String = "MainCategoryPositionMarker.json"
) {
    val context = LocalContext.current
    val repo = remember { PositionMarkerRepository(context) }
    val scope = rememberCoroutineScope()

    val fileStack = remember { mutableStateListOf<String>() }
    var currentFile by remember { mutableStateOf(startFile) }
    var currentRows by remember { mutableStateOf<List<PositionItem>>(emptyList()) }
    var selectedItems by remember { mutableStateOf(linkedSetOf<String>()) }

    // caricamento dinamico
    LaunchedEffect(currentFile) {
        scope.launch {
            currentRows = repo.loadItems(currentFile)
        }
    }

    val isSelectable = currentRows.isNotEmpty() && currentRows.all { it.ref == null }
    val title = when {
        currentFile.equals(startFile, ignoreCase = true) -> "Seleziona Categoria"
        isSelectable -> "Metadati"
        else -> "Sottocategorie"
    }

    val onBackPressed = {
        if (fileStack.isEmpty()) onDismiss()
        else currentFile = fileStack.removeAt(fileStack.lastIndex)
    }

    val onRowClick: (PositionItem) -> Unit = { row ->
        if (!row.ref.isNullOrEmpty()) {
            fileStack.add(currentFile)
            currentFile = row.ref!!
        } else {
            if (selectedItems.contains(row.id)) selectedItems.remove(row.id)
            else selectedItems.add(row.id)
            selectedItems = linkedSetOf<String>().apply { addAll(selectedItems) }
        }
    }

    val onSaveClicked = {
        val resultArray = JSONArray()
        currentRows.filter { selectedItems.contains(it.id) }.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("note", item.note)
            obj.put("sourceFile", currentFile)
            resultArray.put(obj)
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
                TextButton(onClick = onBackPressed) { Text("Indietro") }
                TextButton(onClick = onDismiss) { Text("Annulla") }
                if (isSelectable) {
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
                    row = SelectRow(row.id, row.title, row.note),
                    isSelected = selectedItems.contains(row.id),
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

    // 🔸 Parsing dinamico del JSON interno (row contiene già tutti i campi nel formato JSON)
    val fields = remember(row) {
        try {
            val jsonObj = JSONObject()
            jsonObj.put("id", row.id)
            jsonObj.put("title", row.title)
            if (row.note.isNotEmpty()) jsonObj.put("note", row.note)

            // se row.title o row.note contengono JSON, possiamo anche fare un parse ulteriore
            jsonObj.keys().asSequence().associateWith { key -> jsonObj.get(key).toString() }
        } catch (e: Exception) {
            emptyMap<String, String>()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Mostra dinamicamente ogni coppia chiave/valore
            fields.forEach { (key, value) ->
                if (key != "id") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$key:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Spunta verde se selezionato
        if (isSelectable && isSelected) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selezionato",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}