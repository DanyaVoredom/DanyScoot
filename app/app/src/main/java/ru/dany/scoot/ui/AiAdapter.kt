package ru.dany.scoot.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import ru.dany.scoot.R
import ru.dany.scoot.ai.AiMessage
import ru.dany.scoot.ai.AiRole
import ru.dany.scoot.databinding.ItemAiMessageBinding

class AiAdapter : ListAdapter<AiMessage, AiAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAiMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(private val binding: ItemAiMessageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: AiMessage) {
            val context = binding.root.context
            val user = message.role == AiRole.USER
            val label = when (message.role) {
                AiRole.USER -> R.string.ai_role_user
                AiRole.ASSISTANT -> R.string.ai_role_assistant
                AiRole.TOOL -> R.string.ai_role_tool
                AiRole.ERROR -> R.string.ai_role_error
            }
            val background = when (message.role) {
                AiRole.USER -> com.google.android.material.R.attr.colorPrimaryContainer
                AiRole.ERROR -> com.google.android.material.R.attr.colorErrorContainer
                AiRole.TOOL -> com.google.android.material.R.attr.colorSecondaryContainer
                else -> com.google.android.material.R.attr.colorSurfaceContainerHigh
            }
            val foreground = when (message.role) {
                AiRole.USER -> com.google.android.material.R.attr.colorOnPrimaryContainer
                AiRole.ERROR -> com.google.android.material.R.attr.colorOnErrorContainer
                AiRole.TOOL -> com.google.android.material.R.attr.colorOnSecondaryContainer
                else -> com.google.android.material.R.attr.colorOnSurface
            }
            binding.aiRole.setText(label)
            binding.aiRole.setTextColor(MaterialColors.getColor(binding.aiRole, com.google.android.material.R.attr.colorOnSurfaceVariant))
            binding.aiText.setTextColor(MaterialColors.getColor(binding.aiText, foreground))
            binding.aiText.text = message.text
            binding.aiText.maxWidth = (context.resources.displayMetrics.widthPixels * 0.78f).toInt()
            binding.aiCard.setCardBackgroundColor(MaterialColors.getColor(binding.aiCard, background))
            val params = binding.aiCard.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (user) Gravity.END else Gravity.START
            binding.aiCard.layoutParams = params
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AiMessage>() {
            override fun areItemsTheSame(oldItem: AiMessage, newItem: AiMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AiMessage, newItem: AiMessage): Boolean =
                oldItem == newItem
        }
    }
}
