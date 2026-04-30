package com.example.bilibili.ui.playVideo.danmu

import android.graphics.Color
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R

class DanmuColorAdapter(
    private val onColorSelected: (String) -> Unit
) : ListAdapter<String, DanmuColorAdapter.ColorViewHolder>(ColorDiffCallback()) {

    // 记录当前选中的位置
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_danmu_color, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val colorHex = getItem(position)
        val isSelected = selectedPosition == position

        // 1. 设置颜色块背景 (使用你定义的 v_color_block)
        holder.colorBlock.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorHex))

        // 2. 处理选中状态 (显示你定义的 iv_selected_check)
        holder.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        
        // 3. 点击逻辑
        holder.itemView.setOnClickListener {
            if (selectedPosition != holder.bindingAdapterPosition) {
                val oldPos = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                
                // 仅刷新改变的两项，DiffUtil 会处理这部分，
                // 但因为我们是手动控制 selectedPosition，直接通知这两项刷新即可
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                
                onColorSelected(colorHex)
            }
        }
    }

    fun getSelectedColor(): String = getItem(selectedPosition)

    class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorBlock: View = view.findViewById(R.id.v_color_block)
        val checkIcon: ImageView = view.findViewById(R.id.iv_selected_check)
    }

    // DiffUtil 实现：对比颜色字符串是否相同
    class ColorDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}