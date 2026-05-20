package com.example.bilibili.ui.playVideo.intro

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.databinding.FragmentVideoIntroBinding
import com.example.bilibili.ui.playVideo.PlayVideoViewModel
import com.example.bilibili.ui.user.UserProfileActivity
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.data.api.VideoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VideoIntroFragment : Fragment() {

    private var _binding: FragmentVideoIntroBinding? = null
    private val binding get() = _binding!!

    // 使用 activityViewModels 共享 Activity 的那个 ViewModel
    private val sharedViewModel: PlayVideoViewModel by activityViewModels()

    // 推荐视频适配器
    private var recommendAdapter: RecommendVideoAdapter? = null

    // 在线观看人数轮询相关
    private val onlineHandler = Handler(Looper.getMainLooper())
    private var onlineRunnable: Runnable? = null
    private val onlinePollingInterval = 5000L // 5秒轮询一次

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

        // 5. fileId 就绪后开始上报（接口参数是 fileId，不是 videoId）
        sharedViewModel.fileIdLive.observe(viewLifecycleOwner) { fileId ->
            if (!fileId.isNullOrEmpty()) {
                startOnlinePolling(fileId)
            }
        }

    }

    /**
     * 获取设备ID
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }

    /**
     * 开始在线观看人数轮询
     */
    private fun startOnlinePolling(fileId: String) {
        stopOnlinePolling() // 先停止之前的轮询

        onlineRunnable = object : Runnable {
            override fun run() {
                fetchOnlineCount(fileId)
                onlineHandler.postDelayed(this, onlinePollingInterval)
            }
        }
        onlineHandler.post(onlineRunnable!!)
    }

    /**
     * 停止在线观看人数轮询
     */
    private fun stopOnlinePolling() {
        onlineRunnable?.let {
            onlineHandler.removeCallbacks(it)
            onlineRunnable = null
        }
    }

    /**
     * 获取在线观看人数
     */
    private fun fetchOnlineCount(fileId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val deviceId = getDeviceId()
                val videoService = RetrofitClient.create(VideoService::class.java)
                val response = withContext(Dispatchers.IO) {
                    videoService.reportVideoPlayOnline(fileId, deviceId)
                }

                val jsonObject = JSONObject(response)
                if (jsonObject.optString("status") == "success") {
                    val onlineCount = jsonObject.optInt("data", 0)
                    binding.tvOnlineCount.text = "${onlineCount}人正在看"
                }
            } catch (e: Exception) {
                // 静默处理错误，避免频繁轮询时大量错误日志
            }
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

            tvVideoId.text = "ID: $videoId"
            tvVideoIntroduction.text = info.optString("introduction")

            // 加载推荐视频
            val videoName = info.optString("videoName")
            loadRecommendVideos(videoName, videoId)

            cgTags.removeAllViews() // 刷新前先清空旧标签，防止页面滑动导致数据错乱叠加
            val tagsString = info.optString("tags")
            if (!tagsString.isNullOrEmpty()) {
                val tagList = tagsString.split(",") // 按照逗号切割
                for (tag in tagList) {
                    if (tag.trim().isNotEmpty()) {
                        // 创建一个类似Chip风格的TextView
                        val chipTextView = TextView(requireContext()).apply {
                            text = tag.trim()
                            setBackgroundColor(Color.parseColor("#F6F7F8"))
                            setTextColor(Color.parseColor("#61666D"))
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)

                            // 设置padding
                            val paddingHorizontal = (12 * resources.displayMetrics.density).toInt()
                            val paddingVertical = (6 * resources.displayMetrics.density).toInt()
                            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

                            // 设置圆角背景
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.parseColor("#F6F7F8"))
                                cornerRadius = 100 * resources.displayMetrics.density
                            }
                        }

                        // 设置外边距
                        val layoutParams = android.view.ViewGroup.MarginLayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            val margin = (4 * resources.displayMetrics.density).toInt()
                            setMargins(margin, margin, margin, margin)
                        }

                        chipTextView.layoutParams = layoutParams
                        cgTags.addView(chipTextView)
                    }
                }
            }

            // 数据渲染（点赞、投币、收藏数）
            llLikeCount.text = info.optString("likeCount")
            llCoinCount.text = info.optString("coinCount")
            llFavCount.text = info.optString("collectCount")

            // 点击事件：全部转发给 ViewModel 处理
            llLike.setOnClickListener {
                sharedViewModel.doVideoAction(videoId, 2) { /* 状态由 userActionsLive / videoDetailLive 同步 */ }
            }
            llCoin.setOnClickListener {
                if (binding.llCoinIcon.isSelected) return@setOnClickListener
                sharedViewModel.doVideoAction(videoId, 4, 1) { /* 状态由 userActionsLive 同步 */ }
            }
            llFav.setOnClickListener {
                sharedViewModel.doVideoAction(videoId, 3) { /* 状态由 userActionsLive / videoDetailLive 同步 */ }
            }
        }
    }

    private fun renderAuthorDetails(user: JSONObject) {
        val authorId = user.optString("userId")
        binding.apply {
            tvAuthorName.text = user.optString("nickName")
            tvAuthorStats.text = "${user.optString("fansCount")}粉丝"
            GlideEngine.loadVideoCover(requireContext(), user.optString("avatar"), ivAvatar)

            // 点击头像跳转到 UP 主主页
            ivAvatar.setOnClickListener {
                if (authorId.isNotEmpty()) {
                    val intent = Intent(requireContext(), UserProfileActivity::class.java).apply {
                        putExtra("user_id", authorId)
                    }
                    startActivity(intent)
                }
            }

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
     * 解析视频三连状态（仅处理视频级 userAction，忽略评论点赞/点踩）
     */
    private fun parseActionState(actions: JSONArray?) {
        var liked = false
        var coined = false
        var favorited = false
        if (actions != null) {
            for (i in 0 until actions.length()) {
                val item = actions.getJSONObject(i)
                if (!item.isNull("commentId") && item.optInt("commentId", 0) > 0) {
                    continue
                }
                when (item.optInt("actionType")) {
                    2 -> liked = true
                    3 -> favorited = true
                    4 -> coined = true
                }
            }
        }
        binding.llLikeIcon.isSelected = liked
        binding.llLikeCount.isSelected = liked
        binding.llCoinIcon.isSelected = coined
        binding.llCoinCount.isSelected = coined
        binding.llFavIcon.isSelected = favorited
        binding.llFavCount.isSelected = favorited
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

    private fun initRecommendList() {
        recommendAdapter = RecommendVideoAdapter { recommendItem ->
            // 点击推荐视频跳转播放
            val intent = Intent(requireContext(), com.example.bilibili.ui.playVideo.PlayVideoActivity::class.java).apply {
                putExtra("video_id", recommendItem.videoId)
            }
            startActivity(intent)
        }
        binding.rvRecommend.layoutManager = LinearLayoutManager(context)
        binding.rvRecommend.isNestedScrollingEnabled = false
        binding.rvRecommend.adapter = recommendAdapter
    }

    /**
     * 加载推荐视频
     */
    private fun loadRecommendVideos(videoName: String, videoId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val videoService = RetrofitClient.create(VideoService::class.java)
                val response = withContext(Dispatchers.IO) {
                    videoService.getVideoRecommend(videoName, videoId)
                }

                val jsonObject = JSONObject(response)
                if (jsonObject.optString("status") == "success") {
                    val dataArray = jsonObject.optJSONArray("data")
                    val recommendList = mutableListOf<RecommendVideoItem>()

                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            recommendList.add(
                                RecommendVideoItem(
                                    videoId = item.optString("videoId"),
                                    videoName = item.optString("videoName"),
                                    videoCover = item.optString("videoCover"),
                                    nickName = item.optString("nickName"),
                                    userId = item.optString("userId"),
                                    playCount = item.optInt("playCount", 0),
                                    danmuCount = item.optInt("danmuCount", 0),
                                    commentCount = item.optInt("commentCount", 0),
                                    duration = item.optInt("duration")
                                )
                            )
                        }
                    }

                    recommendAdapter?.submitList(recommendList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun animateExpand(expand: Boolean) {
        // 箭头旋转动画
        val targetRotation = if (expand) 180f else 0f
        val rotateAnimator = android.animation.ObjectAnimator.ofFloat(
            binding.ivExpand, "rotation", binding.ivExpand.rotation, targetRotation
        )
        rotateAnimator.duration = 300
        rotateAnimator.interpolator = android.view.animation.DecelerateInterpolator()
        rotateAnimator.start()

        // 内容展开/收起动画
        if (expand) {
            binding.llExpandableContent.visibility = View.VISIBLE
            binding.llExpandableContent.alpha = 0f
            val fadeAnimator = android.animation.ObjectAnimator.ofFloat(
                binding.llExpandableContent, "alpha", 0f, 1f
            )
            fadeAnimator.duration = 250
            fadeAnimator.interpolator = android.view.animation.DecelerateInterpolator()
            fadeAnimator.start()

            // 标题展开动画
            binding.tvVideoTitle.maxLines = Int.MAX_VALUE
        } else {
            val fadeAnimator = android.animation.ObjectAnimator.ofFloat(
                binding.llExpandableContent, "alpha", 1f, 0f
            )
            fadeAnimator.duration = 200
            fadeAnimator.interpolator = android.view.animation.AccelerateInterpolator()
            fadeAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.llExpandableContent.visibility = View.GONE
                }
            })
            fadeAnimator.start()

            // 标题收起动画
            binding.tvVideoTitle.maxLines = 1
        }
    }

    private fun initExpandLogic() {
        binding.ivExpand.setOnClickListener {
            // 通过大口袋的状态判断当前是展开还是折叠
            val isCurrentlyExpanded = binding.llExpandableContent.visibility == View.VISIBLE
            val params = binding.tvVideoTitle.layoutParams as RelativeLayout.LayoutParams

            val density = resources.displayMetrics.density
            val margin8px = (8 * density).toInt()  // 对应 8dp
            val margin24px = (24 * density).toInt() // 对应 24dp

            if (isCurrentlyExpanded) {
                // 收起
                animateExpand(false)

                // 限制标题不会盖住右边箭头
                params.addRule(RelativeLayout.LEFT_OF, R.id.iv_expand)
                params.marginEnd = margin8px
            } else {
                // 展开
                animateExpand(true)

                // 展开后打破左侧限制，让第二行字能铺满屏幕宽度
                params.removeRule(RelativeLayout.LEFT_OF)
                params.marginEnd = margin24px
            }
            binding.tvVideoTitle.layoutParams = params
        }
    }

    private fun formatCount(count: Int) = if (count >= 10000) String.format("%.1f万", count / 10000.0) else count.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        stopOnlinePolling() // 停止在线人数轮询
        _binding = null
    }
}