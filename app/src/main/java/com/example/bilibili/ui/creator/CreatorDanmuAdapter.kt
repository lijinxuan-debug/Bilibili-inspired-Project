package com.example.bilibili.ui.creator

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.CreatorDanmuItem
import com.example.bilibili.databinding.ItemCreatorDanmuBinding
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.UserInfoText

class CreatorDanmuAdapter(
    private val onDelete: (CreatorDanmuItem) -> Unit,
    private val onUserClick: (CreatorDanmuItem) -> Unit,
    private val onVideoClick: (CreatorDanmuItem) -> Unit,
) : PagingDataAdapter<CreatorDanmuItem, CreatorDanmuAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCreatorDanmuBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
        holder.binding.ivAvatar.setOnClickListener { onUserClick(item) }
        holder.binding.tvNickname.setOnClickListener { onUserClick(item) }
        holder.binding.ivVideoCover.setOnClickListener { onVideoClick(item) }
    }

    class ViewHolder(
        val binding: ItemCreatorDanmuBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CreatorDanmuItem) {
            binding.tvNickname.text = item.nickName
            binding.tvTime.text = item.postTime
            binding.tvContent.text = item.text
            binding.tvPlayTime.text = formatPlayTime(item.playTimeSec)
            val avatarPath = UserInfoText.normalize(item.avatar)
            if (avatarPath.isEmpty()) {
                binding.ivAvatar.setImageResource(R.drawable.ic_avatar_default)
            } else {
                GlideEngine.loadUserAvatar(binding.root.context, avatarPath, binding.ivAvatar)
            }
            GlideEngine.loadVideoCover(
                binding.root.context,
                item.videoCover,
                binding.ivVideoCover,
                cornerRadius = 6,
            )
        }

        private fun formatPlayTime(seconds: Int): String {
            if (seconds <= 0) return "00:00"
            val minutes = seconds / 60
            val remain = seconds % 60
            return "%02d:%02d".format(minutes, remain)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CreatorDanmuItem>() {
        override fun areItemsTheSame(oldItem: CreatorDanmuItem, newItem: CreatorDanmuItem) =
            oldItem.danmuId == newItem.danmuId

        override fun areContentsTheSame(oldItem: CreatorDanmuItem, newItem: CreatorDanmuItem) =
            oldItem == newItem
    }
}
