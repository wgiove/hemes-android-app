package de.adversum.hermescompanion

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Messenger-artiger Chat-Verlauf: User-Blase rechts, KI-Blase links.
 */
class ChatAdapter :
    ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: ConstraintLayout) : RecyclerView.ViewHolder(view) {
        val root: ConstraintLayout = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false) as ConstraintLayout
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

        // Blase rechts (User) wenn isUser, sonst links (Assistant)
        val set = ConstraintSet()
        set.clone(holder.root)
        set.clear(R.id.tvBubble, ConstraintSet.START)
        set.clear(R.id.tvBubble, ConstraintSet.END)
        if (msg.isUser) {
            set.connect(R.id.tvBubble, ConstraintSet.END, R.id.chat_item, ConstraintSet.END)
        } else {
            set.connect(R.id.tvBubble, ConstraintSet.START, R.id.chat_item, ConstraintSet.START)
        }
        set.connect(R.id.tvBubble, ConstraintSet.TOP, R.id.chat_item, ConstraintSet.TOP)
        set.applyTo(holder.root)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a === b
            override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) =
                a.text == b.text && a.role == b.role
        }
    }
}