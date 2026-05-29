package com.example.bilibili.ui.creator

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.CreatorVideoOption
import com.example.bilibili.databinding.ItemPopupCreatorVideoOptionBinding

class CreatorVideoFilterPopupAdapter(
    private val onSelected: (CreatorVideoOption) -> Unit,
) : RecyclerView.Adapter<CreatorVideoFilterPopupAdapter.ViewHolder>() {

    private val items = mutableListOf<CreatorVideoOption>()
    private var selectedVideoId: String? = null

    fun submitList(options: List<CreatorVideoOption>, selectedId: String?) {
        items.clear()
        items.addAll(options)
        selectedVideoId = selectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPopupCreatorVideoOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val selected = item.videoId == selectedVideoId
        holder.bind(item, selected)
        holder.itemView.setOnClickListener {
            selectedVideoId = item.videoId
            notifyDataSetChanged()
            onSelected(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: ItemPopupCreatorVideoOptionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CreatorVideoOption, selected: Boolean) {
            binding.tvTitle.text = item.title
            binding.ivCheck.isVisible = selected
            val colorRes = if (selected) R.color.bili_pink else R.color.bili_text_primary
            binding.tvTitle.setTextColor(ContextCompat.getColor(binding.root.context, colorRes))
        }
    }
}
