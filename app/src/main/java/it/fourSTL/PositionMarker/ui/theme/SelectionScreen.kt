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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Selezioni: NONE = non selezionato, SINGLE = giallo (valido 1 salvataggio), PERSISTENT = verde (persistente)
enum class SelectionState { NONE, SINGLE, PERSISTENT }

// Data class per la riga (assumi che PositionItem abbia id, title, note, ref)
data class SelectRow(val id: String, val title: String, val note: String = "", val ref: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    onDismiss: () -> Unit,
    //onSave: (Map<String, String>, Boolean) -> Unit,       // mantiene la firma che usi
    onSave: (Map<String, String>, Boolean) -> Unit,       // mantiene la firma che usi
    startFile: String = "MainCategoryPositionMarker.json",
    persistentSelections: MutableSet<String>              // set degli id persistenti (mutato direttamente)
) {
    val context = LocalContext.current
    val repo = remember { PositionMarkerRepository(context) }
    val scope = rememberCoroutineScope()

    var currentFile by remember { mutableStateOf(startFile) }
    var currentRows by remember { mutableStateOf<List<PositionItem>>(emptyList()) }

    // stato per ogni id -> SelectionState; inizializziamo con i persistenti già presenti
    var selectionStates by remember { mutableStateOf(mapOf<String, SelectionState>()) }

    LaunchedEffect(currentFile) {
        scope.launch {
            currentRows = repo.loadItems(currentFile)

            // inizializza selectionStates: se l'id è in persistentSelections -> PERSISTENT, altrimenti NONE
            selectionStates = currentRows.associate { row ->
                val st = if (persistentSelections.contains(row.id)) SelectionState.PERSISTENT else SelectionState.NONE
                row.id to st
            }
        }
    }

    fun persistCurrentPersistentMetadata() {
        // costruisce una mappa "nome italiano"/"nome latino" usando il primo elemento persistente (se presente)
        val persistentIds = selectionStates.filterValues { it == SelectionState.PERSISTENT }.keys
        val map = mutableMapOf<String, String>()
        if (persistentIds.isNotEmpty()) {
            // scelta semplice: usa il primo id persistente trovato (seleziona behavior che preferisci)
            val firstId = persistentIds.first()
            val item = currentRows.firstOrNull { it.id == firstId }
            if (item != null) {
                if (item.title.isNotEmpty()) map["nome italiano"] = item.title
                if (item.note.isNotEmpty()) map["nome latino"] = item.note
            }
        }
        // salva nei prefs (usa la funzione globale che hai in MapsForgeMapComposable.kt)
        savePersistentMetadata(context, map)
    }

    val onBackPressed = {
        onDismiss()
    }

    val onRowClick: (PositionItem) -> Unit = { row ->
        if (!row.ref.isNullOrEmpty()) {
            // navigazione verso sottocategoria
            currentFile = row.ref!!
            // reset locale (caricamento avverrà con LaunchedEffect)
        } else {
            // toggle dello stato per la riga
            val current = selectionStates[row.id] ?: SelectionState.NONE
            val next = when (current) {
                SelectionState.NONE -> SelectionState.SINGLE
                SelectionState.SINGLE -> SelectionState.PERSISTENT
                SelectionState.PERSISTENT -> SelectionState.NONE
            }
            selectionStates = selectionStates.toMutableMap().apply { put(row.id, next) }

            // aggiorna persistentSelections e salva prefs se necessario
            if (next == SelectionState.PERSISTENT) {
                persistentSelections.add(row.id)
            } else {
                // se non PERSISTENT, rimuovi dall'insieme persistente (se presente)
                persistentSelections.remove(row.id)
            }

            // dopo aver aggiornato lo stato persistente, risalva la mappa persistente
            persistCurrentPersistentMetadata()
        }
    }

    val onSaveClicked = {
        // prendi tutti gli id selezionati (SINGLE o PERSISTENT)
        val selectedIds = selectionStates.filterValues { it == SelectionState.SINGLE || it == SelectionState.PERSISTENT }.keys

        /*val metadataMap = mutableMapOf<String, String>()
        currentRows.filter { selectedIds.contains(it.id) }.forEach { item ->
            if (item.title.isNotEmpty()) metadataMap["nome italiano"] = item.title
            if (item.note.isNotEmpty()) metadataMap["nome latino"] = item.note
        }

        // Chiamata al callback (secondo argomento non usato qui => false)
        onSave(metadataMap, false)*/

        val selectedRecords = currentRows
            .filter { selectedIds.contains(it.id) }
            .forEach { item ->
                val record = mapOf(
                    "idsp" to item.id,
                    "nome italiano" to item.title,
                    "nome latino" to item.note
                )
                onSave(record, false)
            }

        // resettare solamente gli STATE SINGLE (gialli) dopo il salvataggio; i PERSISTENT rimangono
        selectionStates = selectionStates.mapValues { (_, st) ->
            if (st == SelectionState.SINGLE) SelectionState.NONE else st
        }

        // salvare nuovamente la mappa persistente (nel caso qualcosa sia cambiato)
        persistCurrentPersistentMetadata()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleziona Metadati") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        bottomBar = {
            val anySelected = selectionStates.any { it.value != SelectionState.NONE }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBackPressed) { Text("Indietro") }
                TextButton(onClick = onDismiss) { Text("Annulla") }
                Button(onClick = onSaveClicked, enabled = anySelected) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salva")
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
                val state = selectionStates[row.id] ?: SelectionState.NONE
                SelectionRowItem(
                    row = SelectRow(row.id, row.title, row.note, row.ref),
                    state = state,
                    onClick = { onRowClick(row) }
                )
                Divider()
            }
        }
    }
}

@Composable
fun SelectionRowItem(
    row: SelectRow,
    state: SelectionState,
    onClick: () -> Unit
) {
    val backgroundColor = when (state) {
        SelectionState.NONE -> Color.Transparent
        SelectionState.SINGLE -> Color(0xFFFFEB3B)    // giallo
        SelectionState.PERSISTENT -> Color(0xFFB2FF59) // verde chiaro
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 30.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyMedium)
            if (row.note.isNotEmpty()) {
                Text(
                    row.note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state != SelectionState.NONE) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = if (state == SelectionState.PERSISTENT) "Persistente" else "Selezionato",
                tint = if (state == SelectionState.PERSISTENT) Color(0xFF388E3C) else Color(0xFFB8860B)
            )
        }
    }
}
