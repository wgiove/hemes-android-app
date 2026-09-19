package de.adversum.hermescompanion

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Messenger-artiger Chat-Verlauf: User-Blase rechts, KI-Blase links.
 * Optional mit Bestätigungsaktion (Human-in-the-Loop) für KI-Nachrichten.
 */
class ChatAdapter(
    private val onConfirm: (ChatMessage) -> Unit = {},
    private val onCancel: (ChatMessage) -> Unit = {},
) : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false) as LinearLayout
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        val bubble = holder.root.findViewById<TextView>(R.id.tvBubble)
        bubble.text = msg.text
        bubble.setBackgroundResource(
            if (msg.isUser) R.drawable.bubble_user else R.drawable.bubble_assistant
        )
        bubble.setTextColor(
            bubble.context.getColor(
                if (msg.isUser) android.R.color.white else android.R.color.black
            )
        )
        val lp = bubble.layoutParams as LinearLayout.LayoutParams
        lp.gravity = if (msg.isUser) Gravity.END else Gravity.START
        bubble.layoutParams = lp

        val confirmationRow = holder.root.findViewById<View>(R.id.confirmation_row)
        val confirmBtn = holder.root.findViewById<Button>(R.id.btn_confirm)
        val cancelBtn = holder.root.findViewById<Button>(R.id.btn_cancel)
        val conf = msg.confirmation
        if (conf != null) {
            confirmationRow.visibility = View.VISIBLE
            confirmBtn.text = conf.confirmLabel
            cancelBtn.text = conf.cancelLabel
            confirmBtn.setOnClickListener { onConfirm(msg) }
            cancelBtn.setOnClickListener { onCancel(msg) }
        } else {
            confirmationRow.visibility = View.GONE
            confirmBtn.setOnClickListener(null)
            cancelBtn.setOnClickListener(null)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a === b
            override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) =
                a.text == b.text && a.role == b.role && a.confirmation == b.confirmation
        }
    }
}