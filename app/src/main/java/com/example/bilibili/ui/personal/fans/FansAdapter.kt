package com.example.bilibili.ui.personal.fans

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
    private val onBtnClick: (UserFriend) -> Unit,
    private val onUserClick: (UserFriend) -> Unit
) : RecyclerView.Adapter<FansAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFriendBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = list[position]
        val binding = holder.binding

        // 1. 加载头像和文本信息
        GlideEngine.loadUserAvatar(binding.root.context, user.otherAvatar, binding.ivAvatar)
        binding.root.setOnClickListener { onUserClick(user) }
        binding.ivAvatar.setOnClickListener { onUserClick(user) }
        binding.tvNickname.text = user.otherNickName
        binding.tvDescription.text = if (!user.otherPersonalIntroduction.isNullOrBlank() && user.otherPersonalIntroduction != "null") {
            user.otherPersonalIntroduction
        } else {
            ""
        }

        // 2. 粉丝列表状态判断：根据focusType显示不同状态
        binding.btnFollowAction.apply {
            when (user.focusType) {
                1 -> {
                    // 互相关注
                    setTextColor(Color.parseColor("#999999"))
                    setBackgroundResource(R.drawable.shape_follow_btn_grey)
                    text = "互相关注"
                }
                else -> {
                    // 可以回关
                    setTextColor(Color.parseColor("#FB7299"))
                    setBackgroundResource(R.drawable.shape_follow_btn_pink)
                    text = "回关"
                }
            }
        }

        // 3. 点击回调：在粉丝列表点这个通常是执行"回关"
        binding.btnFollowAction.setOnClickListener { onBtnClick(user) }
    }

    override fun getItemCount() = list.size

    // 刷新数据的方法
    fun updateData(newList: List<UserFriend>) {
        this.list = newList
        notifyDataSetChanged()
    }
}