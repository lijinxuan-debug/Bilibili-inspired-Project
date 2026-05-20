package com.example.bilibili.ui.focus

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.UserFriend
import com.example.bilibili.databinding.ItemFriendBinding
import com.example.bilibili.util.GlideEngine

class FocusOnPagingAdapter(
    private val onActionClick: (UserFriend) -> Unit,
    private val onUserClick: (UserFriend) -> Unit
) : PagingDataAdapter<UserFriend, FocusOnPagingAdapter.ViewHolder>(UserFriendDiffCallback()) {

    class ViewHolder(val binding: ItemFriendBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position) ?: return
        val binding = holder.binding

        // 1. 设置基础信息
        binding.tvNickname.text = user.otherNickName
        binding.tvDescription.text = if (!user.otherPersonalIntroduction.isNullOrBlank() && user.otherPersonalIntroduction != "null") {
            user.otherPersonalIntroduction
        } else {
            ""
        }
        GlideEngine.loadUserAvatar(binding.root.context, user.otherAvatar, binding.ivAvatar)

        binding.root.setOnClickListener { onUserClick(user) }
        binding.ivAvatar.setOnClickListener { onUserClick(user) }

        // 2. 根据 focusType 设置按钮文字和样式
        binding.btnFollowAction.apply {
            setTextColor(Color.parseColor("#999999"))
            setBackgroundResource(R.drawable.shape_follow_btn_grey)

            // 在关注页面中，所有用户都是已关注的，只是区分是否互相关注
            text = if (user.focusType == 1) "互相关注" else "已关注"

            setOnClickListener { onActionClick(user) }
        }
    }

    // DiffCallback用于比较数据项是否相同
    class UserFriendDiffCallback : DiffUtil.ItemCallback<UserFriend>() {
        override fun areItemsTheSame(oldItem: UserFriend, newItem: UserFriend): Boolean {
            return oldItem.otherUserId == newItem.otherUserId
        }

        override fun areContentsTheSame(oldItem: UserFriend, newItem: UserFriend): Boolean {
            return oldItem == newItem
        }
    }
}