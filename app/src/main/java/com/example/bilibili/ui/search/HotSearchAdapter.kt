package com.example.bilibili.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.databinding.ItemHotSearchBinding

class HotSearchAdapter(
    var onSearchItemClick: ((String) -> Unit)? = null
) : ListAdapter<String, HotSearchAdapter.ViewHolder>(HotSearchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHotSearchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val word = getItem(position) // ListAdapter 内置方法
        holder.binding.tvHotKeyword.text = word
        holder.binding.root.setOnClickListener { onSearchItemClick?.invoke(word) }
    }

    class ViewHolder(val binding: ItemHotSearchBinding) : RecyclerView.ViewHolder(binding.root)

    // 2. 这个就是你说的封装好的 Callback，作为参数传给构造函数
    class HotSearchDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}