package com.example.bilibili.ui.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.databinding.ItemMessageFanBinding
import com.example.bilibili.databinding.ItemMessageReplyBinding
import com.example.bilibili.util.GlideEngine

class MessageCategoryAdapter(
    private val pageMode: Int,
    private val onFollowBack: (UserMessageItem) -> Unit,
    private val onDelete: (UserMessageItem) -> Unit,
) : PagingDataAdapter<UserMessageItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = pageMode

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            MessageCategoryActivity.MODE_FANS -> FanHolder(
                ItemMessageFanBinding.inflate(inflater, parent, false),
                onFollowBack,
            )
            else -> ReplyHolder(
                ItemMessageReplyBinding.inflate(inflater, parent, false),
                onDelete,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        when (holder) {
            is ReplyHolder -> holder.bind(item)
            is FanHolder -> holder.bind(item)
        }
    }

    private class ReplyHolder(
        private val binding: ItemMessageReplyBinding,
        private val onDelete: (UserMessageItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserMessageItem) {
            val context = binding.root.context
            binding.tvName.text = item.sendUserName
            binding.tvTime.text = MessageTimeFormatter.format(item.createTimeRaw)
            GlideEngine.loadUserAvatar(context, item.sendUserAvatar, binding.ivAvatar)

            val openProfile = {
                MessageNavigator.openUserProfile(context, item.sendUserId)
            }
            MessageNavigator.bindUserProfileClick(binding.ivAvatar, item.sendUserId, openProfile)
            MessageNavigator.bindUserProfileClick(binding.tvName, item.sendUserId, openProfile)

            binding.btnDelete.setOnClickListener { onDelete(item) }
            binding.root.setOnClickListener {
                MessageNavigator.openMessageTarget(context, item)
            }
            binding.layoutPreviewGroup.setOnClickListener {
                MessageNavigator.openMessageTarget(context, item)
            }

            if (item.isDirectVideoComment) {
                binding.tvAction.text = context.getString(R.string.message_commented_my_video)
                binding.tvContent.text = item.messageContent
                val showCover = item.videoCover.isNotBlank()
                binding.ivPreviewCover.isVisible = showCover
                binding.tvPreview.isVisible = !showCover
                if (showCover) {
                    GlideEngine.loadVideoCover(context, item.videoCover, binding.ivPreviewCover)
                } else {
                    binding.tvPreview.text = item.videoName.ifBlank { "" }
                }
            } else {
                binding.tvAction.text = context.getString(R.string.message_action_reply_comment)
                binding.tvContent.text = item.messageContent
                binding.ivPreviewCover.isVisible = false
                binding.tvPreview.isVisible = true
                binding.tvPreview.text = item.messageContentReply
            }
        }
    }

    private class FanHolder(
        private val binding: ItemMessageFanBinding,
        private val onFollowBack: (UserMessageItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserMessageItem) {
            val context = binding.root.context
            binding.tvName.text = item.sendUserName
            binding.tvTime.text = MessageTimeFormatter.format(item.createTimeRaw)
            GlideEngine.loadUserAvatar(context, item.sendUserAvatar, binding.ivAvatar)

            val openProfile = {
                MessageNavigator.openUserProfile(context, item.sendUserId)
            }
            MessageNavigator.bindUserProfileClick(binding.ivAvatar, item.sendUserId, openProfile)
            MessageNavigator.bindUserProfileClick(binding.tvName, item.sendUserId, openProfile)

            binding.btnFollowBack.setOnClickListener { onFollowBack(item) }
            binding.root.setOnClickListener { openProfile() }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<UserMessageItem>() {
        override fun areItemsTheSame(oldItem: UserMessageItem, newItem: UserMessageItem): Boolean =
            oldItem.messageId == newItem.messageId

        override fun areContentsTheSame(oldItem: UserMessageItem, newItem: UserMessageItem): Boolean =
            oldItem == newItem
    }
}
