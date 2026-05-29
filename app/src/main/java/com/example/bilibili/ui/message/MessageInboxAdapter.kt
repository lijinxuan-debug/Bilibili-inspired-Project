package com.example.bilibili.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.databinding.ItemMessageConversationBinding
import com.example.bilibili.util.GlideEngine

class MessageInboxAdapter(
    private val onItemClick: (UserMessageItem) -> Unit,
) : PagingDataAdapter<UserMessageItem, MessageInboxAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    class ViewHolder(
        private val binding: ItemMessageConversationBinding,
        private val onItemClick: (UserMessageItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserMessageItem) {
            val context = binding.root.context
            binding.tvName.text = item.sendUserName
            binding.tvPreview.text = MessagePreviewFormatter.preview(context, item)
            binding.tvTime.text = MessageTimeFormatter.format(item.createTimeRaw)
            val unread = MessageUnreadCenter.isMessageUnread(item)
            binding.viewUnreadDot.visibility = if (unread) View.VISIBLE else View.GONE
            binding.tvUnreadCount.visibility = View.GONE
            binding.ivAvatarIcon.visibility = View.GONE
            binding.ivAvatar.background = null
            GlideEngine.loadUserAvatar(context, item.sendUserAvatar, binding.ivAvatar)

            val openProfile = {
                MessageNavigator.openUserProfile(context, item.sendUserId)
            }
            MessageNavigator.bindUserProfileClick(binding.ivAvatar, item.sendUserId, openProfile)
            MessageNavigator.bindUserProfileClick(binding.tvName, item.sendUserId, openProfile)

            binding.root.setOnClickListener {
                if (MessageUnreadCenter.isMessageUnread(item)) {
                    onItemClick(item)
                }
                MessageNavigator.openMessageTarget(context, item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<UserMessageItem>() {
        override fun areItemsTheSame(oldItem: UserMessageItem, newItem: UserMessageItem): Boolean =
            oldItem.messageId == newItem.messageId

        override fun areContentsTheSame(oldItem: UserMessageItem, newItem: UserMessageItem): Boolean =
            oldItem == newItem && MessageUnreadCenter.isMessageUnread(oldItem) == MessageUnreadCenter.isMessageUnread(newItem)
    }
}
