package com.example.bilibili.ui.playVideo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.widget.TextView
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.text.InputFilter
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.example.bilibili.MainActivity
import com.example.bilibili.R
import com.example.bilibili.data.model.DanmuEntity
import com.example.bilibili.data.model.PreviewConfigEntity
import com.example.bilibili.databinding.ActivityPlayVideoBinding
import com.example.bilibili.databinding.DialogDanmuSettingBinding
import com.example.bilibili.ui.playVideo.comment.VideoCommentFragment
import com.example.bilibili.ui.playVideo.danmu.DanmuColorAdapter
import com.example.bilibili.ui.playVideo.intro.RecommendVideoItem
import com.example.bilibili.ui.playVideo.intro.VideoIntroFragment
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.ToastUtils
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.bilibili.util.VideoDataUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.shuyu.gsyvideoplayer.GSYVideoManager.releaseAllVideos
import com.shuyu.gsyvideoplayer.utils.CommonUtil
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayVideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayVideoBinding

    private val viewModel: PlayVideoViewModel by viewModels()

    private var currentFileId: String = ""

    private var currentVideoId: String = ""

    /** 播放量延迟刷新任务；Activity 销毁时由 lifecycleScope 自动取消 */
    private var playCountRefreshJob: Job? = null

    private var currentVideoUrl: String? = null

    private var currentVideoTitle: String? = null

    // 当前雪碧图的相关配置
    private var currentPreviewConfig: PreviewConfigEntity? = null

    // 雪碧图大图（只加载一次，拖动进度条时只改 Matrix）
    private var previewSpriteBitmap: Bitmap? = null

    private var wasPlayingBeforeBackground = false

    private var lastPlayPositionMs = 0L

    /** 从后台返回时需要恢复进度（在 onStop 里置 true） */
    private var shouldRestorePlayback = false

    /** 等弹幕接口返回后再开播，避免视频先跑、弹幕后 seek 造成回跳 */
    private var danmakuListReady = false

    private var pendingPlayUrl: String? = null

    private var pendingPlaySeekMs = 0L

    private var cachedDanmakuList: List<DanmuEntity>? = null

    private var danmakuUiEnabled = true

    private var danmuExpandAnimator: ValueAnimator? = null

    private var endRecommendItem: RecommendVideoItem? = null

    /** 推荐跳转在同一 Activity 内切换视频，返回时恢复，避免叠层 Activity 争用 Surface */
    private val videoBackStack = ArrayDeque<VideoPlaybackStackEntry>()

    private data class VideoPlaybackStackEntry(
        val videoId: String,
        val positionMs: Long,
        val wasOnEndScreen: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            lastPlayPositionMs = it.getLong(KEY_PLAY_POSITION, 0L)
            wasPlayingBeforeBackground = it.getBoolean(KEY_WAS_PLAYING, false)
            if (lastPlayPositionMs > 0) {
                shouldRestorePlayback = true
            }
        }
        // 1. 基础 UI 配置
        enableEdgeToEdge()
        binding = ActivityPlayVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val ivGoHome = binding.videoPlayer.findViewById<ImageView>(R.id.iv_go_home)
        ivGoHome?.setOnClickListener {
            // 彻底停掉正在放的视频和音频，释放底层内核
            releaseAllVideos()

            // 跳转到主页 (把 MainActivity 换成你项目里实际的主页 Activity)
            val intent = Intent(this, MainActivity::class.java).apply {
                // 关键：利用这两个 Flag 清空当前所有视频历史页面栈，让主页处于最顶层
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)

            // 关掉当前的播放页面
            finish()
        }

        // 2. 初始化各组件
        initSystemUI()
        initViewPager()

        // 3. 注册数据观察者 (在请求数据前先订阅)
        initObservers()

        // 4. 获取 Intent 数据并触发加载
        val videoId = intent.getStringExtra("video_id") ?: ""
        if (videoId.isNotEmpty()) {
            // 调用 ViewModel 里的全量加载函数
            viewModel.fetchAllData(videoId)
        } else {
            ToastUtils.showShort(this, "视频 ID 缺失")
        }

        binding.ivStickyBack.setOnClickListener { handleBackNavigation() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackNavigation()
                }
            },
        )
        setupStickyBackWithPlayerControls()

        initDanmuSwitch()

        binding.videoPlayer.setAutoFullWithSize(true)
        binding.videoPlayer.setRotateViewAuto(false)
        binding.videoPlayer.setNeedOrientationUtils(false)

        // 全屏：仅窗口内放大，不旋转到横屏
        binding.videoPlayer.fullscreenButton.setOnClickListener {
            binding.ivStickyBack.visibility = View.GONE
            binding.videoPlayer.startWindowFullscreen(this, true, true)
        }

        setupPreviewSeekListener()
        setupPlaybackEndScreen()
    }

    /** 与播放器控制栏同步显隐；全屏时隐藏（全屏页自带返回）；结束页常显 */
    private fun setupStickyBackWithPlayerControls() {
        binding.ivStickyBack.visibility = View.GONE
        binding.videoPlayer.setOnControlBarVisibilityListener { controlsVisible ->
            binding.ivStickyBack.visibility = when {
                binding.videoPlayer.isEndScreenShowing -> View.VISIBLE
                binding.videoPlayer.isIfCurrentIsFullscreen -> View.GONE
                controlsVisible -> View.VISIBLE
                else -> View.GONE
            }
        }
    }

    private fun setupPlaybackEndScreen() {
        val player = binding.videoPlayer

        player.setOnPlaybackEndListener {
            binding.ivStickyBack.visibility = View.VISIBLE
            bindEndRecommendCard(endRecommendItem)
        }

        player.findViewById<View>(R.id.layout_end_recommend_card)?.setOnClickListener {
            val item = endRecommendItem ?: return@setOnClickListener
            openRecommendVideo(item.videoId)
        }

        player.findViewById<View>(R.id.btn_end_replay)?.setOnClickListener {
            player.replayFromBeginning()
            binding.ivStickyBack.visibility = View.GONE
        }

        viewModel.recommendListLive.observe(this) { list ->
            endRecommendItem = list.firstOrNull()
            if (player.isEndScreenShowing) {
                bindEndRecommendCard(endRecommendItem)
            }
        }
    }

    private fun bindEndRecommendCard(item: RecommendVideoItem?) {
        val player = binding.videoPlayer
        val card = player.findViewById<View>(R.id.layout_end_recommend_card) ?: return
        val recommendTitle = player.findViewById<View>(R.id.tv_end_recommend_title)
        val replayBtn = player.findViewById<View>(R.id.btn_end_replay) ?: return

        if (item == null) {
            recommendTitle?.visibility = View.GONE
            card.visibility = View.GONE
            layoutEndReplayCentered(replayBtn, true)
            return
        }
        recommendTitle?.visibility = View.VISIBLE
        card.visibility = View.VISIBLE
        layoutEndReplayCentered(replayBtn, false)

        val titleView = player.findViewById<TextView>(R.id.tv_end_video_title)
        val uploaderView = player.findViewById<TextView>(R.id.tv_end_uploader)
        val playCountView = player.findViewById<TextView>(R.id.tv_end_play_count)
        val commentCountView = player.findViewById<TextView>(R.id.tv_end_comment_count)
        val durationView = player.findViewById<TextView>(R.id.tv_end_duration)
        val coverView = player.findViewById<ImageView>(R.id.iv_end_cover)

        val rawTitle = item.videoName ?: ""
        val formattedTitle = rawTitle
            .replace("<span class='highlight'>", "<font color='#FB7299'>")
            .replace("</span>", "</font>")
        titleView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(formattedTitle, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(formattedTitle)
        }

        uploaderView.text = item.nickName
        playCountView.text = VideoDataUtils.formatCount(item.playCount)
        commentCountView.text = VideoDataUtils.formatCount(item.commentCount)

        val durationSeconds = item.duration
        if (durationSeconds != null && durationSeconds > 0) {
            durationView.text = VideoDataUtils.formatDuration(durationSeconds)
            durationView.visibility = View.VISIBLE
        } else {
            durationView.visibility = View.GONE
        }

        GlideEngine.loadVideoCover(this, item.videoCover, coverView)
    }

    /** 无推荐时把重播居中；有推荐时重播在左下角 */
    private fun layoutEndReplayCentered(replayBtn: View, centered: Boolean) {
        val lp = replayBtn.layoutParams as? ConstraintLayout.LayoutParams ?: return
        if (centered) {
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomMargin = 0
            lp.topMargin = 0
        } else {
            lp.topToTop = ConstraintLayout.LayoutParams.UNSET
            lp.endToEnd = ConstraintLayout.LayoutParams.UNSET
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomMargin = (16 * resources.displayMetrics.density).toInt()
        }
        replayBtn.layoutParams = lp
    }

    private fun hidePlaybackEndScreen() {
        binding.videoPlayer.hideEndOverlay()
    }

    /** 不要覆盖 GSY 的 SeekBar 监听器；通过播放器回调统一处理预览 */
    private fun setupPreviewSeekListener() {
        binding.videoPlayer.setOnPreviewSeekListener(object :
            DanmakuVideoPlayer.OnPreviewSeekListener {
            override fun onSeekStart() {
                if (currentPreviewConfig != null) {
                    getPreviewLayout()?.visibility = View.VISIBLE
                }
            }

            override fun onSeekPreview(seekTimeMs: Long, totalTimeMs: Long) {
                updatePreviewAtTime(seekTimeMs, totalTimeMs)
            }

            override fun onSeekEnd() {
                getPreviewLayout()?.visibility = View.GONE
            }
        })
    }

    private fun getPreviewLayout() =
        binding.videoPlayer.findViewById<androidx.cardview.widget.CardView>(R.id.layout_preview_layout)

    private fun getPreviewImage() =
        binding.videoPlayer.findViewById<ImageView>(R.id.iv_preview_image)

    private fun getPreviewTimeText() =
        binding.videoPlayer.findViewById<android.widget.TextView>(R.id.tv_preview_time)

    private fun initDanmuSwitch() {
        binding.ivDanmuSwitch.isSelected = danmakuUiEnabled
        binding.videoPlayer.setDanmaKuShow(danmakuUiEnabled)
        updateDanmuInputExpanded(danmakuUiEnabled, animate = false)

        binding.ivDanmuSwitch.setOnClickListener {
            danmakuUiEnabled = !danmakuUiEnabled
            binding.ivDanmuSwitch.isSelected = danmakuUiEnabled
            binding.videoPlayer.setDanmaKuShow(danmakuUiEnabled)
            updateDanmuInputExpanded(danmakuUiEnabled, animate = true)
        }

        binding.danmu.setOnClickListener {
            if (danmakuUiEnabled) {
                danmuSheetDialog()
            }
        }
    }

    /** 裁剪容器宽度收缩/展开，内部文案不被挤压换行 */
    private fun updateDanmuInputExpanded(expanded: Boolean, animate: Boolean) {
        val clipHost = binding.flDanmuExpandClip
        val content = binding.llDanmuExpandable
        danmuExpandAnimator?.cancel()

        if (!animate) {
            if (expanded) {
                clipHost.visibility = View.VISIBLE
                clipHost.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                content.scaleX = 1f
            } else {
                clipHost.visibility = View.GONE
                content.scaleX = 1f
            }
            clipHost.requestLayout()
            return
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        content.measure(widthSpec, heightSpec)
        val targetWidth = content.measuredWidth

        if (expanded) {
            clipHost.visibility = View.VISIBLE
            content.scaleX = 1f
            clipHost.layoutParams = clipHost.layoutParams.apply { width = 0 }
            clipHost.requestLayout()

            danmuExpandAnimator = ValueAnimator.ofInt(0, targetWidth).apply {
                duration = DANMU_EXPAND_ANIM_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    clipHost.layoutParams.width = animation.animatedValue as Int
                    clipHost.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        clipHost.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                })
                start()
            }
        } else {
            val startWidth = if (clipHost.width > 0) clipHost.width else targetWidth
            if (startWidth <= 0) {
                clipHost.visibility = View.GONE
                return
            }
            danmuExpandAnimator = ValueAnimator.ofInt(startWidth, 0).apply {
                duration = DANMU_EXPAND_ANIM_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    clipHost.layoutParams.width = animation.animatedValue as Int
                    clipHost.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        clipHost.visibility = View.GONE
                        clipHost.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        content.scaleX = 1f
                    }
                })
                start()
            }
        }
    }

    private fun danmuSheetDialog() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetStyle)
        val dialogBinding = DialogDanmuSettingBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var isStylePanelMode = false

        dialogBinding.etDanmuMessage.filters = arrayOf(
            InputFilter { source, _, _, _, _, _ ->
                source.toString().replace("\n", "")
            }
        )

        fun showInputMode(showKeyboard: Boolean = true) {
            isStylePanelMode = false
            dialogBinding.ivDanmuStyleBtn.setImageResource(R.drawable.ic_danmu_style_a)
            if (showKeyboard) {
                dialogBinding.etDanmuMessage.requestFocus()
                dialogBinding.etDanmuMessage.post {
                    imm.showSoftInput(
                        dialogBinding.etDanmuMessage,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }
            }
        }

        fun showStylePanelMode() {
            isStylePanelMode = true
            dialogBinding.ivDanmuStyleBtn.setImageResource(R.drawable.ic_danmu_style_p)
            dialogBinding.etDanmuMessage.clearFocus()
            imm.hideSoftInputFromWindow(dialogBinding.etDanmuMessage.windowToken, 0)
        }

        dialogBinding.ivDanmuStyleBtn.setOnClickListener {
            if (isStylePanelMode) {
                showInputMode()
            } else {
                showStylePanelMode()
            }
        }

        dialogBinding.etDanmuMessage.setOnClickListener {
            if (isStylePanelMode) {
                showInputMode()
            }
        }

        dialogBinding.etDanmuMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isStylePanelMode) {
                showInputMode()
            }
        }

        val colorAdapter = DanmuColorAdapter { color ->
            // 暂时不需要预览
        }

        dialogBinding.rvDanmuColors.apply {
            layoutManager = GridLayoutManager(this@PlayVideoActivity, 5) // 每行5个
            adapter = colorAdapter
        }

        colorAdapter.submitList(viewModel.danmuColors)

        // 弹幕位置按钮逻辑 (你已经写的)
        val posButtons =
            listOf(dialogBinding.btnPosScroll, dialogBinding.btnPosTop, dialogBinding.btnPosBottom)
        posButtons.forEach { layout ->
            layout.setOnClickListener {
                posButtons.forEach { it.isSelected = false }
                layout.isSelected = true
            }
        }
        posButtons[0].isSelected = true

        // 发送按钮
        dialogBinding.ivDanmuSend.setOnClickListener {
            val content = dialogBinding.etDanmuMessage.text.toString().trim()
            if (content.isEmpty()) {
                ToastUtils.showShort(this, "请输入弹幕内容")
                return@setOnClickListener
            }

            // 构造弹幕对象
            val entity = DanmuEntity().apply {
                this.text = content
                this.color = colorAdapter.getSelectedColor()
                this.time = (binding.videoPlayer.currentPositionWhenPlaying / 1000).toInt()
                this.mode = when {
                    dialogBinding.btnPosTop.isSelected -> 1
                    dialogBinding.btnPosBottom.isSelected -> 2
                    else -> 0
                }
                this.videoId = currentVideoId
                this.fileId = currentFileId
            }

            // 需要喂给播放器
            binding.videoPlayer.addDanmakuEntity(entity)

            // 发送到服务端进行存储
            viewModel.sendDanmu(entity)

            imm.hideSoftInputFromWindow(dialogBinding.etDanmuMessage.windowToken, 0)

            bottomSheetDialog.dismiss()
        }

        dialogBinding.etDanmuMessage.postDelayed({
            showInputMode()
        }, 200)

        bottomSheetDialog.show()
    }


    /**
     * 专门负责观察 ViewModel 中的 LiveData 数据变化
     */
    private fun initObservers() {
        // 观察播放地址：一旦拿到 M3U8 地址，立刻初始化 GSYPlayer
        viewModel.videoUrlLive.observe(this) { url ->
            if (url.isNullOrEmpty()) return@observe
            val sameUrl = url == currentVideoUrl
            currentVideoUrl = url
            currentVideoTitle = "视频播放"
            // 从后台回来 LiveData 会再回调一次，不能再次 initPlayer 否则从头播
            if (sameUrl) {
                if (binding.videoPlayer.currentPlayer != null) {
                    restorePlaybackIfNeeded()
                } else {
                    reinitPlayerAtLastPosition(url)
                }
                return@observe
            }
            danmakuListReady = false
            pendingPlayUrl = url
            pendingPlaySeekMs = if (shouldRestorePlayback) lastPlayPositionMs else 0L
            shouldRestorePlayback = false
            tryStartPlayerWhenReady()
        }

        // 观察视频详情
        viewModel.videoDetailLive.observe(this) { videoInfo ->
            currentVideoId = videoInfo.optString("videoId")
        }

        viewModel.fileIdLive.observe(this) { fileId ->
            if (!fileId.isNullOrEmpty()) {
                currentFileId = fileId
            }
        }

        // 观察错误信息
        viewModel.errorLive.observe(this) { errorMsg ->
            ToastUtils.showShort(this, errorMsg)
        }

        // 观察弹幕数据：先缓存，与播放地址都就绪后再灌入播放器并开播
        viewModel.danmuListLive.observe(this) { danmuEntities ->
            cachedDanmakuList = danmuEntities
            danmakuListReady = true
            tryStartPlayerWhenReady()
        }

        // 观察雪碧图数据
        viewModel.previewConfigLive.observe(this) { config ->
            if (config != null && config.url.isNotEmpty()) {
                this.currentPreviewConfig = config
                loadPreviewSprite(config.url)
            }
        }
    }

    /** 雪碧图只下载一次；CustomTarget 必须配合 asBitmap() 并指定宽高 */
    private fun loadPreviewSprite(url: String) {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>(SIZE_ORIGINAL, SIZE_ORIGINAL) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    previewSpriteBitmap = resource
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    previewSpriteBitmap = null
                }
            })
    }

    private fun updatePreviewAtTime(currentTimeMs: Long, totalTimeMs: Long) {
        val config = currentPreviewConfig ?: return
        val previewImage = getPreviewImage() ?: return
        val previewTimeText = getPreviewTimeText()

        previewTimeText?.text =
            "${CommonUtil.stringForTime(currentTimeMs)} / ${CommonUtil.stringForTime(totalTimeMs)}"

        if (config.interval <= 0) return
        var frameIndex = (currentTimeMs / 1000.0 / config.interval).toInt()
        if (frameIndex < 0) frameIndex = 0
        if (frameIndex >= config.total) frameIndex = config.total - 1

        val frameBitmap = cropPreviewFrame(config, frameIndex) ?: return
        previewImage.scaleType = ImageView.ScaleType.CENTER_CROP
        previewImage.setImageBitmap(frameBitmap)
    }

    /** 从雪碧图大图裁切单帧，避免 Matrix 只平移导致露出 3x3 九宫格 */
    private fun cropPreviewFrame(config: PreviewConfigEntity, frameIndex: Int): Bitmap? {
        val source = previewSpriteBitmap ?: return null
        val col = frameIndex % config.col
        val row = frameIndex / config.col
        val x = col * config.frameW
        val y = row * config.frameH
        if (x + config.frameW > source.width || y + config.frameH > source.height) return null
        return Bitmap.createBitmap(source, x, y, config.frameW, config.frameH)
    }

    /**
     * 系统级 UI 适配：处理刘海屏和状态栏占位
     */
    private fun initSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        // 刘海屏/水滴屏适配：给播放器容器添加状态栏高度的 padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.playerContainer) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.playerContainer.setPadding(0, statusBarHeight, 0, 0)
            insets
        }
    }

    /**
     * 设置 ViewPager2 (简介与评论切换)
     */
    private fun initViewPager() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment {
                return if (position == 0) VideoIntroFragment() else VideoCommentFragment()
            }
        }

        // 绑定 TabLayout 与 ViewPager2
        TabLayoutMediator(binding.videoTabLayout, binding.viewPager) { tab, position ->
            if (position == 0) {
                tab.text = "简介"
            } else {
                // 观察评论总数变化
                viewModel.commentTotalCount.observe(this@PlayVideoActivity) { totalCount ->
                    if (tab.text.toString().startsWith("评论")) {
                        tab.text = "评论 $totalCount"
                    }
                }
                // 初始设置
                val currentCount = viewModel.commentTotalCount.value ?: 0
                tab.text = "评论 $currentCount"
            }
        }.attach()

        // 监听 Tab 切换事件
        binding.videoTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    // 切换到评论Tab时，让底部栏立即可见
                    binding.viewPager.postDelayed({
                        binding.viewPager.requestLayout()
                    }, 100)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * 后端播放量异步入库（Redis 队列），无推送；延迟后再请求 getVideoInfo 刷新简介区数字。
     * 同一视频只保留一个任务，避免 tryStartPlayerWhenReady 多次触发重复刷新。
     */
    private fun schedulePlayCountUiRefresh() {
        playCountRefreshJob?.cancel()
        playCountRefreshJob = lifecycleScope.launch {
            delay(2500)
            val videoId = currentVideoId.ifEmpty {
                viewModel.videoDetailLive.value?.optString("videoId").orEmpty()
            }
            if (videoId.isNotEmpty()) {
                viewModel.refreshVideoDetail(videoId)
            }
        }
    }

    /** 弹幕先 prepare，再开播，与 GSY 官方一致，避免视频先跑、弹幕后 seek 弹回 */
    private fun tryStartPlayerWhenReady() {
        val url = pendingPlayUrl ?: return
        if (!danmakuListReady) return
        val list = cachedDanmakuList ?: return
        val title = currentVideoTitle ?: "视频播放"
        val seekMs = pendingPlaySeekMs
        pendingPlayUrl = null
        pendingPlaySeekMs = 0L
        binding.videoPlayer.resetDanmakuForNewVideo()
        binding.videoPlayer.setDanmakuList(list)
        initPlayer(url, title, seekMs)
        schedulePlayCountUiRefresh()
    }

    private fun initPlayer(url: String, title: String, seekMs: Long = 0L) {
        hidePlaybackEndScreen()
        binding.videoPlayer.onVideoReset()

        // 关闭边播边缓存，避免缓存损坏导致卡顿、破音
        binding.videoPlayer.setUp(url, false, null)
        binding.videoPlayer.setIsTouchWiget(true)
        binding.videoPlayer.setReleaseWhenLossAudio(false)
        binding.videoPlayer.backButton.visibility = View.GONE

        if (seekMs > 0) {
            binding.videoPlayer.setDanmakuStartSeekPosition(seekMs)
        }
        // 释放后需等 Surface 重建再开播，否则易出现 BufferQueue abandoned
        binding.videoPlayer.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            binding.videoPlayer.startPlayLogic()
            if (seekMs > 0) {
                binding.videoPlayer.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        binding.videoPlayer.seekTo(seekMs)
                    }
                }, 300)
            }
        }, 150)
    }

    private fun snapshotPlaybackState() {
        try {
            val pos = binding.videoPlayer.currentPositionWhenPlaying
            if (pos > 0) {
                lastPlayPositionMs = pos
            }
            val state = binding.videoPlayer.currentPlayer?.currentState
            wasPlayingBeforeBackground = state == GSYVideoView.CURRENT_STATE_PLAYING
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 从简介/结束页推荐切换视频：不新开 Activity，避免 GSY 与 Surface 被顶掉导致黑屏。
     */
    fun openRecommendVideo(videoId: String) {
        if (videoId.isBlank() || videoId == currentVideoId) return
        snapshotPlaybackState()
        if (currentVideoId.isNotEmpty()) {
            videoBackStack.addLast(
                VideoPlaybackStackEntry(
                    videoId = currentVideoId,
                    positionMs = lastPlayPositionMs,
                    wasOnEndScreen = binding.videoPlayer.isEndScreenShowing,
                ),
            )
        }
        switchToVideo(videoId, restorePositionMs = 0L)
    }

    private fun handleBackNavigation() {
        if (popAndRestorePreviousVideo()) return
        finish()
    }

    private fun popAndRestorePreviousVideo(): Boolean {
        val entry = videoBackStack.removeLastOrNull() ?: return false
        switchToVideo(entry.videoId, restorePositionMs = entry.positionMs)
        return true
    }

    private fun switchToVideo(videoId: String, restorePositionMs: Long) {
        lastPlayPositionMs = restorePositionMs
        shouldRestorePlayback = false
        currentVideoUrl = null
        danmakuListReady = false
        cachedDanmakuList = null
        pendingPlayUrl = null
        playCountRefreshJob?.cancel()
        previewSpriteBitmap = null
        releasePlayerBeforeSwitch()
        viewModel.fetchAllData(videoId)
    }

    private fun releasePlayerBeforeSwitch() {
        hidePlaybackEndScreen()
        try {
            binding.videoPlayer.onVideoPause()
            binding.videoPlayer.currentPlayer?.release()
            binding.videoPlayer.release()
        } catch (_: Exception) {
        }
    }

    private fun reinitPlayerAtLastPosition(url: String) {
        danmakuListReady = cachedDanmakuList != null
        pendingPlayUrl = url
        pendingPlaySeekMs = lastPlayPositionMs
        tryStartPlayerWhenReady()
    }

    private fun restorePlaybackIfNeeded() {
        if (!shouldRestorePlayback) return
        shouldRestorePlayback = false
        val url = currentVideoUrl
        try {
            val player = binding.videoPlayer
            if (!url.isNullOrEmpty() && player.currentPlayer == null) {
                reinitPlayerAtLastPosition(url)
                return
            }
            if (player.currentPlayer == null) return
            if (player.isEndScreenShowing) return
            player.onVideoResume()
            // 等 surface 恢复后再读当前进度；后台音频若一直在播，livePos 会大于 onStop 时保存的值
            player.post {
                val livePos = player.currentPositionWhenPlaying
                val targetPos = maxOf(lastPlayPositionMs, livePos)
                lastPlayPositionMs = targetPos
                // 仅当内核进度明显落后（被重置）时才 seek，避免把 35 秒拽回 31 秒
                if (livePos + 800 < targetPos) {
                    player.seekTo(targetPos)
                }
                player.syncDanmakuOnce(targetPos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        snapshotPlaybackState()
        outState.putLong(KEY_PLAY_POSITION, lastPlayPositionMs)
        outState.putBoolean(KEY_WAS_PLAYING, wasPlayingBeforeBackground)
    }

    override fun onPause() {
        super.onPause()
        // 不调用 onVideoPause()，切后台时让音频继续；顺便记一下进度（onStop 时还会再记一次）
        if (!isFinishing) {
            snapshotPlaybackState()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) return
        snapshotPlaybackState()
        shouldRestorePlayback = true
        // 不切后台暂停弹幕：视频音频继续播时弹幕时钟也应继续，回前台再 seek 对齐即可
    }

    override fun onResume() {
        super.onResume()
        restorePlaybackIfNeeded()
    }

    companion object {
        private const val KEY_PLAY_POSITION = "play_position"
        private const val KEY_WAS_PLAYING = "was_playing"
        private const val DANMU_EXPAND_ANIM_MS = 220L
    }

    override fun onDestroy() {
        playCountRefreshJob?.cancel()
        playCountRefreshJob = null
        videoBackStack.clear()
        super.onDestroy()
        danmuExpandAnimator?.cancel()

        previewSpriteBitmap = null

        try {
            binding.videoPlayer.currentPlayer?.release()
            binding.videoPlayer.release()
            releaseAllVideos()
        } catch (e: Exception) {
            // 忽略释放异常
        }
    }
}