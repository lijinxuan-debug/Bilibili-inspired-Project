package com.example.bilibili.ui.playVideo.intro

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.databinding.FragmentVideoIntroBinding
import com.example.bilibili.ui.playVideo.PlayVideoViewModel
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.SPUtils
import org.json.JSONArray
import org.json.JSONObject

class VideoIntroFragment : Fragment() {

    private var _binding: FragmentVideoIntroBinding? = null
    private val binding get() = _binding!!

    // 使用 activityViewModels 共享 Activity 的那个 ViewModel
    private val sharedViewModel: PlayVideoViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVideoIntroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecommendList()
        initExpandLogic()

        // 1. 监听视频主数据 (标题、点赞数、时间等)
        sharedViewModel.videoDetailLive.observe(viewLifecycleOwner) { videoInfo ->
            renderVideoInfo(videoInfo)
        }

        // 2. 监听用户交互状态 (点赞、投币、收藏的状态)
        sharedViewModel.userActionsLive.observe(viewLifecycleOwner) { actions ->
            parseActionState(actions)
        }

        // 3. 监听作者数据 (头像、名字、粉丝数)
        sharedViewModel.authorLive.observe(viewLifecycleOwner) { userInfo ->
            renderAuthorDetails(userInfo)
        }

        // 4. 监听关注状态 (ViewModel 独立维护这个状态)
        sharedViewModel.isFollowedLive.observe(viewLifecycleOwner) { followed ->
            updateFollowUI(followed)
        }

    }

    @SuppressLint("SetTextI18n")
    private fun renderVideoInfo(info: JSONObject) {
        val videoId = info.optString("videoId")
        binding.apply {
            tvVideoTitle.text = info.optString("videoName")
            tvPlayCount.text = formatCount(info.optInt("playCount"))
            tvDanmakuCount.text = info.optString("danmuCount")
            tvPostTime.text = info.optString("createTime")

            // 数据渲染
            llLikeCount.text = info.optString("likeCount")
            llCoinCount.text = info.optString("coinCount")
            llFavCount.text = info.optString("collectCount")

            // 点击事件：全部转发给 ViewModel 处理
            llLike.setOnClickListener {
                sharedViewModel.doVideoAction(videoId, 2) { success ->
                    if (success) handleLocalActionUpdate(llLikeIcon, llLikeCount)
                }
            }
            llCoin.setOnClickListener {
                sharedViewModel.doVideoAction(videoId, 4, 1) { success ->
                    if (success) handleLocalActionUpdate(llCoinIcon, llCoinCount)
                }
            }
            llFav.setOnClickListener {
                sharedViewModel.doVideoAction(videoId, 3) { success ->
                    if (success) handleLocalActionUpdate(llFavIcon, llFavCount)
                }
            }
        }
    }

    private fun renderAuthorDetails(user: JSONObject) {
        val authorId = user.optString("userId")
        binding.apply {
            tvAuthorName.text = user.optString("nickName")
            tvAuthorStats.text = "${user.optString("fansCount")}粉丝"
            GlideEngine.loadVideoCover(requireContext(), user.optString("avatar"), ivAvatar)

            // 如果是当前用户的视频则没有关注按钮
            if (SPUtils.getUserId() == authorId) {
                btnFollow.visibility = View.GONE
            }

            // 关注点击
            btnFollow.setOnClickListener {
                sharedViewModel.toggleFollow(authorId)
            }
        }
    }

    /**
     * 辅助方法：解析初始三连状态
     */
    private fun parseActionState(actions: JSONArray?) {
        if (actions == null) return
        var (l, c, f) = Triple(false, false, false)
        for (i in 0 until actions.length()) {
            val type = actions.getJSONObject(i).optInt("actionType")
            if (type == 2) l = true
            if (type == 4) c = true
            if (type == 3) f = true
        }
        binding.llLikeIcon.isSelected = l
        binding.llLikeCount.isSelected = l
        binding.llCoinIcon.isSelected = c
        binding.llCoinCount.isSelected = c
        binding.llFavIcon.isSelected = f
        binding.llFavCount.isSelected = f
    }

    /**
     * UI 辅助：更新关注按钮样式
     */
    private fun updateFollowUI(followed: Boolean) {
        binding.btnFollow.apply {
            text = if (followed) "已关注" else "+ 关注"
            setTextColor(if (followed) Color.parseColor("#999999") else Color.WHITE)
            setBackgroundResource(if (followed) R.drawable.bg_followed_button else R.drawable.bg_follow_button)
        }
    }

    /**
     * UI 辅助：点击后的局部数字和颜色切换（非请求逻辑）
     */
    @SuppressLint("SetTextI18n")
    private fun handleLocalActionUpdate(icon: View, text: TextView) {
        val newState = !icon.isSelected
        icon.isSelected = newState
        text.isSelected = newState
        val num = text.text.toString().toIntOrNull() ?: 0
        text.text = (if (newState) num + 1 else (num - 1).coerceAtLeast(0)).toString()
    }

    private fun initRecommendList() {
        binding.rvRecommend.layoutManager = LinearLayoutManager(context)
        binding.rvRecommend.isNestedScrollingEnabled = false
    }

    private fun initExpandLogic() {
        binding.ivExpand.setOnClickListener {
            val isExpanded = binding.tvVideoTitle.maxLines == Int.MAX_VALUE
            binding.tvVideoTitle.maxLines = if (isExpanded) 1 else Int.MAX_VALUE
            binding.ivExpand.rotation = if (isExpanded) 0f else 180f
        }
    }

    private fun formatCount(count: Int) = if (count >= 10000) String.format("%.1f万", count / 10000.0) else count.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}