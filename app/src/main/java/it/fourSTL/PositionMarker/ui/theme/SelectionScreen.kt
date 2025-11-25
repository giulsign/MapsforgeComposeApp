package it.fourSTL.PositionMarker

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Selezioni: NONE = non selezionato, SINGLE = giallo (valido 1 salvataggio), PERSISTENT = verde (persistente)
enum class SelectionState { NONE, SINGLE, PERSISTENT }

// Data class per la riga
data class SelectRow(val id: String, val title: String, val note: String = "", val ref: String? = null)

// 🔹 FUNZIONI per salvare/caricare le selezioni persistenti
fun savePersistentSelectionsSet(context: Context, selections: Set<String>) {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    prefs.edit().putStringSet("persistent_selections_set", selections).apply()
}

fun loadPersistentSelectionsSet(context: Context): MutableSet<String> {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    val saved = prefs.getStringSet("persistent_selections_set", emptySet()) ?: emptySet()
    return saved.toMutableSet()
}

// 🔹 Funzione per salvare i DETTAGLI di ogni elemento persistente
fun savePersistentItemDetails(context: Context, itemId: String, title: String, note: String) {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    val detailsKey = "persistent_item_$itemId"
    val json = JSONObject().apply {
        put("id", itemId)
        put("title", title)
        put("note", note)
    }
    prefs.edit().putString(detailsKey, json.toString()).apply()
}

fun removePersistentItemDetails(context: Context, itemId: String) {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    prefs.edit().remove("persistent_item_$itemId").apply()
}

fun clearAllPersistentData(context: Context) {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)

    // Carica tutti gli ID persistenti per pulire i loro dettagli
    val persistentIds = loadPersistentSelectionsSet(context)
    persistentIds.forEach { id ->
        prefs.edit().remove("persistent_item_$id").apply()
    }

    // Pulisci il set di selezioni
    prefs.edit().remove("persistent_selections_set").apply()

    // Pulisci anche i metadati persistenti (vecchio sistema)
    prefs.edit().remove("persistent_metadata").apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    onDismiss: () -> Unit,
    onSave: (Map<String, String>, Boolean) -> Unit,
    startFile: String = "MainCategoryPositionMarker.json",
    persistentSelections: MutableSet<String>
) {
    val context = LocalContext.current
    val repo = remember { PositionMarkerRepository(context) }
    val scope = rememberCoroutineScope()

    var currentFile by remember { mutableStateOf(startFile) }
    var currentRows by remember { mutableStateOf<List<PositionItem>>(emptyList()) }

    // Stack per la navigazione
    var navigationStack by remember { mutableStateOf(listOf<String>()) }

    // stato per ogni id -> SelectionState
    var selectionStates by remember { mutableStateOf(mapOf<String, SelectionState>()) }

    // Stati per la ricerca
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PositionItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var showAddPersonalNoteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentFile) {
        scope.launch {
            currentRows = repo.loadItems(currentFile)

            // Inizializza selectionStates: se l'id è in persistentSelections -> PERSISTENT
            selectionStates = currentRows.associate { row ->
                val st = if (persistentSelections.contains(row.id)) SelectionState.PERSISTENT else SelectionState.NONE
                row.id to st
            }
        }
    }


    // 🔹 FUNZIONE CORRETTA: Salva TUTTE le selezioni persistenti
    fun savePersistentSelectionsToPrefs() {
        // Salva il set di ID persistenti
        savePersistentSelectionsSet(context, persistentSelections)

        // Per retrocompatibilità, salva anche la mappa metadati del primo elemento
        val persistentIds = selectionStates.filterValues { it == SelectionState.PERSISTENT }.keys
        val map = mutableMapOf<String, String>()

        if (persistentIds.isNotEmpty()) {
            val firstId = persistentIds.first()
            val itemsToSearch = if (isSearchActive)
                (currentRows + searchResults).distinctBy { it.id }
            else
                currentRows

            val item = itemsToSearch.firstOrNull { it.id == firstId }
            if (item != null) {
                map["idsp"] = item.id
                if (item.title.isNotEmpty()) map["nome italiano"] = item.title
                if (item.note.isNotEmpty()) map["nome latino"] = item.note
            }
        }

        savePersistentMetadata(context, map)
    }



    // Funzione di ricerca ricorsiva
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
            isSearchActive = false
            searchQuery = ""
            searchResults = emptyList()
        } else if (navigationStack.isNotEmpty()) {
            val previousFile = navigationStack.last()
            navigationStack = navigationStack.dropLast(1)
            currentFile = previousFile
        } else {
            onDismiss()
        }
    }

    val onRowClick: (PositionItem) -> Unit = { row ->
        if (!row.ref.isNullOrEmpty()) {
            // Navigazione verso sottocategoria
            navigationStack = navigationStack + currentFile
            currentFile = row.ref!!
        } else {
            // Toggle dello stato
            val current = selectionStates[row.id] ?: SelectionState.NONE
            val next = when (current) {
                SelectionState.NONE -> SelectionState.SINGLE
                SelectionState.SINGLE -> SelectionState.PERSISTENT
                SelectionState.PERSISTENT -> SelectionState.NONE
            }
            selectionStates = selectionStates.toMutableMap().apply { put(row.id, next) }

            // 🔹 AGGIORNA persistentSelections e salva i dettagli
            if (next == SelectionState.PERSISTENT) {
                persistentSelections.add(row.id)
                savePersistentItemDetails(context, row.id, row.title, row.note)
            } else {
                persistentSelections.remove(row.id)
                removePersistentItemDetails(context, row.id)
            }
        }
    }

    val onSaveClicked = {
        // Prendi TUTTI gli id selezionati (SINGLE + PERSISTENT)
        val selectedIds = selectionStates.filterValues {
            it == SelectionState.SINGLE || it == SelectionState.PERSISTENT
        }.keys

        if (selectedIds.isNotEmpty()) {
            // Cerca l'elemento nei risultati correnti o di ricerca
            val itemsToSearch = if (isSearchActive)
                (currentRows + searchResults).distinctBy { it.id }
            else
                currentRows

            val selectedItem = itemsToSearch.firstOrNull { selectedIds.contains(it.id) }

            if (selectedItem != null) {
                // Crea la mappa con tutti i campi
                val metadataMap = mutableMapOf<String, String>()
                metadataMap["idsp"] = selectedItem.id
                if (selectedItem.title.isNotEmpty()) {
                    metadataMap["nome italiano"] = selectedItem.title
                }
                if (selectedItem.note.isNotEmpty()) {
                    metadataMap["nome latino"] = selectedItem.note
                }

                onSave(metadataMap, false)
            }
        }

        // Resetta solo gli stati SINGLE (gialli)
        selectionStates = selectionStates.mapValues { (_, st) ->
            if (st == SelectionState.SINGLE) SelectionState.NONE else st
        }
        // Salva nuovamente le selezioni persistenti
        savePersistentSelectionsToPrefs()
    }

    if (showAddPersonalNoteDialog) {
        AddPersonalNoteDialog(
            onDismiss = { showAddPersonalNoteDialog = false },
            onSave = { nota, notaB ->
                scope.launch {
                    val success = savePersonalNote(context, nota, notaB)
                    if (success) {
                        // Ricarica la lista
                        currentRows = repo.loadItems(currentFile)
                    }
                }
                showAddPersonalNoteDialog = false
            }
        )
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
                                if (newQuery.length >= 4) {
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
                            placeholder = { Text("Search metadatas...") },
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
                        Text("Select Metadatas")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = emptyList()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Erase search")
                        }
                    }
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            searchResults = emptyList()
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentFile == "personale.json") {
                FloatingActionButton(onClick = { showAddPersonalNoteDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add personal category")
                }
            }
        },
        bottomBar = {
            val anySelected = selectionStates.any { it.value != SelectionState.NONE }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBackPressed) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSaveClicked, enabled = anySelected) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Indicatore di caricamento
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Info risultati ricerca
            if (isSearchActive && searchQuery.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = if (searchQuery.length < 2) {
                            "Search with a t least 2 characters"
                        } else {
                            "${searchResults.size} category found"
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
fun AddPersonalNoteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var nota by remember { mutableStateOf("") }
    var notaB by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Personal Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Notation A") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notaB,
                    onValueChange = { notaB = it },
                    label = { Text("Notation B") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nota, notaB) },
                enabled = nota.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun savePersonalNote(context: Context, nota: String, notaB: String): Boolean {
    val file = File(context.filesDir, "personale.json")

    return try {
        val jsonArray = if (file.exists() && file.readText().isNotBlank()) {
            JSONArray(file.readText())
        } else {
            JSONArray()
        }

        val newId = if (jsonArray.length() > 0) {
            jsonArray.getJSONObject(jsonArray.length() - 1).getInt("id") + 1
        } else {
            1
        }

        val newEntry = JSONObject().apply {
            put("id", newId)
            put("idsp", "PERS$newId")
            put("nota", nota)
            put("nota_b", notaB)
        }

        jsonArray.put(newEntry)
        file.writeText(jsonArray.toString(2))
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
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
                contentDescription = if (state == SelectionState.PERSISTENT) "Persistent" else "Selected",
                tint = if (state == SelectionState.PERSISTENT) Color(0xFF388E3C) else Color(0xFFB8860B)
            )
        }
    }
}