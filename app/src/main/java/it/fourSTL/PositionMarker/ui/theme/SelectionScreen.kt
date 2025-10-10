package it.fourSTL.PositionMarker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
    onSave: (Map<String, String>, Boolean) -> Unit,
    startFile: String = "MainCategoryPositionMarker.json",
    persistentSelections: MutableSet<String>              // set degli id persistenti (mutato direttamente)
) {
    val context = LocalContext.current
    val repo = remember { PositionMarkerRepository(context) }
    val scope = rememberCoroutineScope()

    var currentFile by remember { mutableStateOf(startFile) }
    var currentRows by remember { mutableStateOf<List<PositionItem>>(emptyList()) }

    // Stack per la navigazione: tiene traccia dei file visitati
    var navigationStack by remember { mutableStateOf(listOf<String>()) }

    // stato per ogni id -> SelectionState; inizializziamo con i persistenti già presenti
    var selectionStates by remember { mutableStateOf(mapOf<String, SelectionState>()) }

    // Stati per la ricerca
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PositionItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

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
                map["idsp"] = item.id
                if (item.title.isNotEmpty()) map["nome italiano"] = item.title
                if (item.note.isNotEmpty()) map["nome latino"] = item.note
            }
        }
        // salva nei prefs (usa la funzione globale che hai in MapsForgeMapComposable.kt)
        savePersistentMetadata(context, map)
    }

    // Funzione di ricerca ricorsiva nei file JSON
    suspend fun searchInAllFiles(query: String): List<PositionItem> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<PositionItem>()
        val queryLower = query.lowercase().trim()
        val visitedFiles = mutableSetOf<String>()
        val filesToVisit = mutableListOf("MainCategoryPositionMarker.json")

        while (filesToVisit.isNotEmpty()) {
            val file = filesToVisit.removeAt(0)
            if (file in visitedFiles) continue
            visitedFiles.add(file)

            val items = repo.loadItems(file)
            items.forEach { item ->
                // Cerca solo nei metadati (righe senza ref)
                if (item.ref.isNullOrEmpty()) {
                    val titleMatch = item.title.lowercase().contains(queryLower)
                    val noteMatch = item.note.lowercase().contains(queryLower)

                    if (titleMatch || noteMatch) {
                        results.add(item)
                    }
                }
                // Aggiungi sottocategorie da visitare
                if (!item.ref.isNullOrEmpty()) {
                    filesToVisit.add(item.ref!!)
                }
            }
        }

        return results
    }

    val onBackPressed = {
        if (isSearchActive) {
            // Se la ricerca è attiva, disattivala
            isSearchActive = false
            searchQuery = ""
            searchResults = emptyList()
        } else if (navigationStack.isNotEmpty()) {
            // Naviga indietro nello stack
            val previousFile = navigationStack.last()
            navigationStack = navigationStack.dropLast(1)
            currentFile = previousFile
        } else {
            // Se siamo al file iniziale, chiudi la schermata
            onDismiss()
        }
    }

    val onRowClick: (PositionItem) -> Unit = { row ->
        if (!row.ref.isNullOrEmpty()) {
            // Aggiungi il file corrente allo stack prima di navigare
            navigationStack = navigationStack + currentFile
            // Naviga verso la sottocategoria
            currentFile = row.ref!!
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
        // ✅ LOGICA CORRETTA: Prendi TUTTI gli id selezionati (SINGLE + PERSISTENT)
        val selectedIds = selectionStates.filterValues {
            it == SelectionState.SINGLE || it == SelectionState.PERSISTENT
        }.keys

        if (selectedIds.isNotEmpty()) {
            // 🔹 Trova l'elemento selezionato (prende il primo se ce ne sono più)
            val itemsToSearch = if (isSearchActive) searchResults else currentRows
            val selectedItem = itemsToSearch.firstOrNull { selectedIds.contains(it.id) }

            if (selectedItem != null) {
                // ✅ Crea la mappa con TUTTI i campi incluso idsp
                val metadataMap = mutableMapOf<String, String>()
                metadataMap["idsp"] = selectedItem.id  // ✅ SEMPRE presente quando c'è selezione
                if (selectedItem.title.isNotEmpty()) {
                    metadataMap["nome italiano"] = selectedItem.title
                }
                if (selectedItem.note.isNotEmpty()) {
                    metadataMap["nome latino"] = selectedItem.note
                }

                // Chiama onSave con i metadati completi
                onSave(metadataMap, false)
            }
        }

        // Resetta solo gli stati SINGLE (gialli), i PERSISTENT (verdi) rimangono
        selectionStates = selectionStates.mapValues { (_, st) ->
            if (st == SelectionState.SINGLE) SelectionState.NONE else st
        }

        // Salva nuovamente i metadati persistenti
        persistCurrentPersistentMetadata()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { newQuery ->
                                searchQuery = newQuery
                                if (newQuery.length >= 2) {
                                    isSearching = true
                                    scope.launch {
                                        searchResults = searchInAllFiles(newQuery)
                                        // Inizializza gli stati per i risultati di ricerca
                                        searchResults.forEach { item ->
                                            if (!selectionStates.containsKey(item.id)) {
                                                val st = if (persistentSelections.contains(item.id))
                                                    SelectionState.PERSISTENT
                                                else
                                                    SelectionState.NONE
                                                selectionStates = selectionStates + (item.id to st)
                                            }
                                        }
                                        isSearching = false
                                    }
                                } else {
                                    searchResults = emptyList()
                                }
                            },
                            placeholder = { Text("Cerca metadati...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Seleziona Metadati")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = emptyList()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancella ricerca")
                        }
                    }
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            searchResults = emptyList()
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Cerca")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mostra indicatore di caricamento durante la ricerca
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Mostra info sui risultati
            if (isSearchActive && searchQuery.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = if (searchQuery.length < 2) {
                            "Inserisci almeno 2 caratteri"
                        } else {
                            "${searchResults.size} risultati trovati"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Lista dei risultati
            val displayItems = if (isSearchActive && searchQuery.length >= 2) {
                searchResults
            } else {
                currentRows
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayItems) { row ->
                    val state = selectionStates[row.id] ?: SelectionState.NONE
                    SelectionRowItem(
                        row = SelectRow(row.id, row.title, row.note, row.ref),
                        state = state,
                        onClick = { onRowClick(row) }
                    )
                    HorizontalDivider()
                }
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