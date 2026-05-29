package com.example.bilibili.ui.playVideo.intro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.PlayVideoPartItem
import com.example.bilibili.databinding.ItemPlayVideoPartCardBinding

class PlayVideoPartCardAdapter(
    private val onPartClick: (PlayVideoPartItem) -> Unit,
) : RecyclerView.Adapter<PlayVideoPartCardAdapter.PartViewHolder>() {

    private val items = mutableListOf<PlayVideoPartItem>()
    var selectedFileId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    fun submitList(parts: List<PlayVideoPartItem>, selectedId: String?) {
        items.clear()
        items.addAll(parts)
        selectedFileId = selectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartViewHolder {
        val binding = ItemPlayVideoPartCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return PartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartViewHolder, position: Int) {
        holder.bind(items[position], items[position].fileId == selectedFileId)
    }

    override fun getItemCount(): Int = items.size

    inner class PartViewHolder(
        private val binding: ItemPlayVideoPartCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(part: PlayVideoPartItem, selected: Boolean) {
            val context = binding.root.context
            binding.tvPartTitle.text = part.displayTitle()
            binding.root.setOnClickListener { onPartClick(part) }

            if (selected) {
                binding.tvPartTitle.setTextColor(ContextCompat.getColor(context, R.color.bili_pink))
                binding.ivPlaying.visibility = View.VISIBLE
            } else {
                binding.tvPartTitle.setTextColor(0xFF18191C.toInt())
                binding.ivPlaying.visibility = View.GONE
            }
        }
    }
}
