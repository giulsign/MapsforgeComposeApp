package it.fourSTL.PositionMarker // o il tuo package corretto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback // Importa per il nuovo gestore del tasto indietro
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.fourSTL.PositionMarker.R // Assicurati che R sia importato correttamente
import org.json.JSONArray
import org.json.JSONObject

data class SelectRow(val id: String, val title: String, val note: String = "")

class SelectionActivity : AppCompatActivity(), TableAdapter.RowClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var btnBack: Button
    private lateinit var btnBackToMap: Button
    private lateinit var btnSave: Button

    private enum class Level { CATEGORIA, SOTTOCATEGORIA, METADATI }
    private var currentLevel = Level.CATEGORIA

    private var selectedCategoria: SelectRow? = null
    private var selectedSottocategoria: SelectRow? = null
    private val selectedMetadataIds = linkedSetOf<String>() // mantiene ordine inserimento

    // demo data (sostituisci con i tuoi dati reali)
    private val categorie = listOf(
        SelectRow("cat1", "Categoria 1", "descrizione 1"),
        SelectRow("cat2", "Categoria 2", "descrizione 2"),
        SelectRow("cat3", "Categoria 3", "descrizione 3"),
        SelectRow("cat4", "Categoria 4", "descrizione 4"),
        SelectRow("cat5", "Categoria 5", "descrizione 5")
    )

    private val sottocategorieMap: Map<String, List<SelectRow>> = mapOf(
        "cat1" to (1..5).map { SelectRow("cat1_sub$it", "Opzione $it", "tipo $it") },
        "cat2" to (1..5).map { SelectRow("cat2_sub$it", "Opzione $it", "tipo $it") },
        "cat3" to (1..5).map { SelectRow("cat3_sub$it", "Opzione $it", "tipo $it") },
        "cat4" to (1..5).map { SelectRow("cat4_sub$it", "Opzione $it", "tipo $it") },
        "cat5" to (1..5).map { SelectRow("cat5_sub$it", "Opzione $it", "tipo $it") }
    )

    private val metadataMap: Map<String, List<SelectRow>> = run {
        val map = mutableMapOf<String, List<SelectRow>>()
        for ((_, list) in sottocategorieMap) {
            for (sub in list) {
                val rows = (1..5).map { i -> SelectRow("${sub.id}_md$i", "Metadato $i", "valore $i") }
                map[sub.id] = rows
            }
        }
        map
    }

    private lateinit var adapter: TableAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection)

        recyclerView = findViewById(R.id.recycler_table)
        titleView = findViewById(R.id.title_view)
        btnBack = findViewById(R.id.btn_back)
        btnBackToMap = findViewById(R.id.btn_back_to_map)
        btnSave = findViewById(R.id.btn_save)

        adapter = TableAdapter(emptyList(), this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // Gestione moderna del tasto "Indietro"
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Logica personalizzata per il tasto indietro
                when (currentLevel) {
                    Level.CATEGORIA -> {
                        // Siamo al livello più alto, imposta il risultato e permetti
                        // al sistema di chiudere l'activity.
                        setResult(Activity.RESULT_CANCELED)
                        // Disabilita questo callback e chiama il comportamento predefinito
                        // per evitare loop se questo callback è l'unico.
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed() // Richiama per il comportamento standard
                    }
                    Level.SOTTOCATEGORIA -> {
                        showCategories()
                    }
                    Level.METADATI -> {
                        selectedSottocategoria?.let {
                            showSottocategorie(it)
                        } ?: showCategories() // Fallback se selectedSottocategoria è null
                    }
                }
            }
        })

        btnBack.setOnClickListener {
            // Invoca la logica definita in onBackPressedDispatcher
            onBackPressedDispatcher.onBackPressed()
        }

        btnBackToMap.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        btnSave.setOnClickListener { onSaveClicked() }

        showCategories()
    }

    private fun showCategories() {
        currentLevel = Level.CATEGORIA
        titleView.text = "Seleziona Categoria"
        adapter.updateRows(categorie, selectable = false, preselected = selectedMetadataIds)
        btnSave.visibility = View.GONE
    }

    private fun showSottocategorie(cat: SelectRow) {
        currentLevel = Level.SOTTOCATEGORIA
        selectedCategoria = cat
        titleView.text = "Sottocategorie di ${cat.title}"
        val list = sottocategorieMap[cat.id] ?: emptyList()
        adapter.updateRows(list, selectable = false, preselected = selectedMetadataIds)
        btnSave.visibility = View.GONE
    }

    private fun showMetadata(sub: SelectRow) {
        currentLevel = Level.METADATI
        selectedSottocategoria = sub
        titleView.text = "Metadati — ${selectedCategoria?.title ?: ""} / ${sub.title}"
        val list = metadataMap[sub.id] ?: emptyList()
        adapter.updateRows(list, selectable = true, preselected = selectedMetadataIds)
        btnSave.visibility = View.VISIBLE
    }

    override fun onRowClick(row: SelectRow) {
        when (currentLevel) {
            Level.CATEGORIA -> showSottocategorie(row)
            Level.SOTTOCATEGORIA -> showMetadata(row)
            Level.METADATI -> {
                if (selectedMetadataIds.contains(row.id)) {
                    selectedMetadataIds.remove(row.id)
                } else {
                    selectedMetadataIds.add(row.id)
                }
                adapter.toggleSelection(row.id)
            }
        }
    }

    private fun onSaveClicked() {
        val resultArray = JSONArray()
        val currentCategoriaTitle = selectedCategoria?.title ?: ""
        val currentSottocategoriaTitle = selectedSottocategoria?.title ?: ""

        // Crea una mappa per un accesso efficiente ai dettagli dei metadati
        val idToRowMap = metadataMap.values.flatten().associateBy { it.id }

        for (id in selectedMetadataIds) {
            idToRowMap[id]?.let { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("title", r.title)
                obj.put("note", r.note)
                obj.put("categoria", currentCategoriaTitle)
                obj.put("sottocategoria", currentSottocategoriaTitle)
                resultArray.put(obj)
            }
        }

        val out = Intent()
        out.putExtra("selected_metadata_json", resultArray.toString())
        setResult(Activity.RESULT_OK, out)
        finish()
    }

    // Non è più necessario sovrascrivere onBackPressed() direttamente
    // se si usa OnBackPressedCallback come mostrato sopra.
    // Se lo si mantenesse, dovrebbe chiamare super.onBackPressed() o
    // la logica del dispatcher.
    // Per pulizia, lo rimuoviamo se OnBackPressedCallback è il metodo principale.
    /*
    override fun onBackPressed() {
        // La logica è ora in OnBackPressedCallback
        super.onBackPressed() // O rimuovi completamente questo override
    }
    */
}
