package icather.pages.dev

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import icather.pages.dev.db.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var conversations: List<Conversation>,
    private val isSelectionMode: Boolean,
    private val onItemClicked: (Long) -> Unit,
    private val onItemLongClicked: (Conversation, View) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.bind(conversation, isSelectionMode, selectedItems.contains(conversation.id))

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(conversation.id)
            } else {
                onItemClicked(conversation.id)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                onItemLongClicked(conversation, it)
            }
            true
        }
    }

    override fun getItemCount() = conversations.size

    private fun toggleSelection(conversationId: Long) {
        if (selectedItems.contains(conversationId)) {
            selectedItems.remove(conversationId)
        } else {
            selectedItems.add(conversationId)
        }
        notifyDataSetChanged() 
        onSelectionChanged()
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(conversations.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun getSelectedItems(): LongArray {
        return selectedItems.toLongArray()
    }

    fun getSelectedItemCount(): Int {
        return selectedItems.size
    }

    fun updateData(newConversations: List<Conversation>) {
        conversations = newConversations
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.historyTitle)
        private val timestamp: TextView = itemView.findViewById(R.id.historyTimestamp)
        private val checkBox: CheckBox = itemView.findViewById(R.id.historyCheckbox)

        fun bind(conversation: Conversation, isSelectionMode: Boolean, isSelected: Boolean) {
            title.text = conversation.title
            timestamp.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(conversation.startTime))

            if (isSelectionMode) {
                checkBox.visibility = View.VISIBLE
                checkBox.isChecked = isSelected
            } else {
                checkBox.visibility = View.GONE
            }
        }
    }
}