package it.fourSTL.PositionMarker // Assicurati che il package sia corretto

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.fourSTL.PositionMarker.R // Importa R dal tuo modulo app principale

class TableAdapter(
    private var rows: List<SelectRow>,
    private val rowClickListener: RowClickListener,
    private var selectable: Boolean = false,private val preselectedIds: MutableSet<String> = mutableSetOf()
) : RecyclerView.Adapter<TableAdapter.ViewHolder>() {

    interface RowClickListener {
        fun onRowClick(row: SelectRow)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Definisci le View all'interno del tuo layout di riga (item_select_row.xml)
        // Questi ID devono corrispondere a quelli che definirai nel file XML
        private val titleTextView: TextView = itemView.findViewById(R.id.row_title_textview)
        private val noteTextView: TextView = itemView.findViewById(R.id.row_note_textview)

        fun bind(row: SelectRow) {
            titleTextView.text = row.title
            noteTextView.text = row.note

            // Gestisci lo stato di selezione visiva se 'selectable' è true
            if (selectable) {
                itemView.isActivated = preselectedIds.contains(row.id)
            } else {
                itemView.isActivated = false // Resetta lo stato se non selezionabile
            }

            itemView.setOnClickListener {
                rowClickListener.onRowClick(row)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Gonfia il layout XML per una singola riga
        // Assicurati di aver creato 'R.layout.item_select_row'
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    /**
     * Aggiorna i dati nell'adapter e notifica il RecyclerView.
     */
    fun updateRows(newRows: List<SelectRow>, selectable: Boolean, preselected: Set<String>) {
        this.rows = newRows
        this.selectable = selectable
        this.preselectedIds.clear()
        this.preselectedIds.addAll(preselected)
        notifyDataSetChanged() // Notifica al RecyclerView che i dati sono cambiati
    }

    /**
     *  Gestisce il cambio di stato di selezione per una riga specifica.
     *  Chiamato da SelectionActivity quando un elemento metadata viene cliccato.
     */
    fun toggleSelection(rowId: String) {
        if (!selectable) return // Non fare nulla se le righe non sono attualmente selezionabili

        if (preselectedIds.contains(rowId)) {
            preselectedIds.remove(rowId)
        } else {
            preselectedIds.add(rowId)
        }

        // Trova l'indice dell'elemento e notifica solo quel cambiamento per efficienza
        // e per aggiornare lo stato visivo.
        val index = rows.indexOfFirst { it.id == rowId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }
}
