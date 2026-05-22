package com.example.bilibili.ui.memberShip

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.UserFriend
import com.example.bilibili.databinding.ItemFriendBinding
import com.example.bilibili.util.GlideEngine

class FansAdapter(
    private var list: List<UserFriend>,
    private val onActionClick: (UserFriend) -> Unit
) : RecyclerView.Adapter<FansAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFriendBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = list[position]
        val binding = holder.binding

        // 1. 设置基础信息
        binding.tvNickname.text = user.otherNickName
        binding.tvDescription.text = com.example.bilibili.util.UserInfoText.displayIntroduction(
            user.otherPersonalIntroduction
        )
        GlideEngine.loadUserAvatar(binding.root.context, user.otherAvatar, binding.ivAvatar)

        // 2. 根据 focusType 设置按钮文字
        binding.btnFollowAction.apply {
            // 根据关注类型设置按钮文字
            // focusType: 1-互相关注, 0-未关注, 其他值也可能是已关注
            text = when (user.focusType) {
                1 -> {
                    setTextColor(Color.parseColor("#999999"))
                    setBackgroundResource(R.drawable.shape_follow_btn_grey)
                    "互相关注"
                }
                0 -> "回关"
                else -> "已关注"
            }

            setOnClickListener { onActionClick(user) }
        }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<UserFriend>) {
        this.list = newList
        notifyDataSetChanged()
    }
}