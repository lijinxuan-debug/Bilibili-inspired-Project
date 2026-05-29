package com.example.bilibili.ui.releaseVideo

import android.annotation.SuppressLint
import android.app.Dialog
import android.animation.ObjectAnimator
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.ReleaseVideoPart
import com.example.bilibili.data.api.FileService
import com.example.bilibili.databinding.ActivityReleaseVideoBinding
import com.example.bilibili.databinding.DialogAgreementBinding
import com.example.bilibili.databinding.ItemSimpleTagBinding
import com.example.bilibili.databinding.LayoutPartMoreMenuBinding
import com.example.bilibili.ui.releaseVideo.bottomSheet.AuthorStatementBottomSheetDialogFragment
import com.example.bilibili.ui.releaseVideo.bottomSheet.IntroductionBottomSheetDialogFragment
import com.example.bilibili.ui.releaseVideo.bottomSheet.PartTitleBottomSheetDialogFragment
import com.example.bilibili.ui.convention.CommunityConventionActivity
import com.example.bilibili.ui.releaseVideo.bottomSheet.TagBottomSheetDialogFragment
import androidx.core.content.ContextCompat
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.fixHorizontalScrollConflictWithParent
import com.example.bilibili.util.PermissionHelper
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import com.example.bilibili.util.VideoThumbnailUtil
import com.example.bilibili.util.UiUtils.dp
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.config.SelectModeConfig
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class ReleaseVideoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
    }

    private lateinit var binding: ActivityReleaseVideoBinding

    private val viewModel: ReleaseVideoViewModel by viewModels()

    private lateinit var partAdapter: ReleaseVideoPartAdapter

    private lateinit var partItemTouchHelper: ItemTouchHelper

    private var partMenuDimView: View? = null
    private var hasCustomCover: Boolean = false
    private var hasInitDefaultCover: Boolean = false
    private var lastPartCount: Int = 0
    private var topProgressAnimator: ObjectAnimator? = null
    private var topProgressPartId: String? = null
    private var lastTopShownProgress = 0

    private var pendingPickerAction: PartPickerAction = PartPickerAction.Add

    private sealed class PartPickerAction {
        data object Add : PartPickerAction()
        data class Replace(val partId: String) : PartPickerAction()
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityReleaseVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { requestExitPage() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestExitPage()
            }
        })

        setupVideoPartsList()
        setupPublishAgreement()

        val editVideoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (!editVideoId.isNullOrBlank()) {
            applyEditModeUi()
            viewModel.loadVideoForEdit(editVideoId)
        }
        val videoPath = intent.getStringExtra("video_path")
        val videoDuration = intent.getLongExtra("video_duration", 0)
        if (videoPath != null && videoDuration > 0) {
            viewModel.addPart(videoPath, videoDuration)
        }

        binding.addShard.setOnClickListener {
            if (viewModel.isAnyPartUploading()) {
                ToastUtils.showShort(this, getString(R.string.release_wait_upload_finish))
                return@setOnClickListener
            }
            pendingPickerAction = PartPickerAction.Add
            PermissionHelper.requestPublishVideo(this) { openPartVideoPicker() }
        }
        binding.flCoverEntry.setOnClickListener {
            PermissionHelper.requestGalleryImage(this) { openCoverImagePicker() }
        }

        // 监听标题的输入
        binding.etTitle.doOnTextChanged { text, _, _, _ ->
            val length = text?.length ?: 0
            binding.titleLength.text = "$length/80"

            // 同时更新 ViewModel 中的标题
            viewModel.setVideoTitle(text?.toString() ?: "")
        }

        // 监听分区和标签一栏点击事件
        binding.tagsPartition.setOnClickListener {
            showTagsAndPartition()
        }

        // 监听滚动视图根布局点击事件
        binding.llScrollRoot.setOnClickListener {
            showTagsAndPartition()
        }

        // 创作声明点击事件
        binding.creativeStatement.setOnClickListener {
            val statementSheet = AuthorStatementBottomSheetDialogFragment()
            statementSheet.show(supportFragmentManager, "AuthorStatement")
        }

        // 监听简介点击事件
        binding.introduction.setOnClickListener {
            val introductionSheet = IntroductionBottomSheetDialogFragment()
            introductionSheet.show(supportFragmentManager, "Introduction")
        }

        binding.btnPublish.setOnClickListener {
            if (!viewModel.isPublishReady()) {
                ToastUtils.showShort(this, viewModel.publishBlockReason())
                return@setOnClickListener
            }
            if (viewModel.shouldRetainUploadsOnServer()) {
                ToastUtils.showShort(this, getString(R.string.release_posting_wait))
                return@setOnClickListener
            }
            if (binding.checkboxAgreement.isChecked) {
                publishVideo()
            } else {
                showAgreementDialog()
            }
        }
        updatePublishButtonVisual()

        viewModel.uploadToast.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                ToastUtils.showShort(this, message)
            }
        }

        viewModel.videoParts.observe(this) { parts ->
            partAdapter.setParts(parts)
            applyAutoCoverFromFirstPart(parts)
            if (parts.size > lastPartCount && parts.isNotEmpty() && !partAdapter.isDragging()) {
                binding.rvVideoParts.post {
                    binding.rvVideoParts.smoothScrollToPosition(parts.lastIndex)
                }
            }
            lastPartCount = parts.size
            updateAddPartButtonState()
            refreshUploadUi()
            updatePublishButtonVisual()
        }

        viewModel.canPublish.observe(this) {
            updatePublishButtonVisual()
        }

        viewModel.selectedPartId.observe(this) { selectedId ->
            partAdapter.setSelectedPartId(selectedId)
        }

        // 监听投稿状态
        viewModel.postStatus.observe(this) { status ->
            when (status) {
                "投稿成功", "保存成功" -> {
                    ToastUtils.showShort(this, status)
                    Log.d("ReleaseVideo", status)
                    finish()
                }
                "" -> {
                    // 初始状态，不显示
                }
                else -> {
                    // 显示失败提示
                    ToastUtils.showShort(this, status)
                    Log.e("ReleaseVideo", "投稿状态: $status")
                }
            }
        }

        // 监听选择的分区
        viewModel.selectedPartition.observe(this) { item ->
            val label = viewModel.formatPartitionDisplay(item)
            binding.partition.text = label.ifBlank { getString(R.string.release_partition_hint) }
        }

        // 监听创作声明类型变化
        viewModel.statementType.observe(this) { type ->
            val text = when (type) {
                ReleaseVideoViewModel.StatementType.ORIGINAL -> "视频自制"
                ReleaseVideoViewModel.StatementType.REPOST -> "视频转载"
            }
            binding.reprintOrMakeYourOwn.text = text
        }

        viewModel.editLoadedLive.observe(this) { loaded ->
            if (loaded != true) return@observe
            applyEditModeUi()
            binding.etTitle.setText(viewModel.videoTitle.value.orEmpty())
            binding.titleLength.text = "${binding.etTitle.text.length}/80"
            val cover = viewModel.videoCoverUrl.value
            if (!cover.isNullOrBlank() && cover != "default_cover.jpg") {
                hasCustomCover = true
                GlideEngine.loadVideoCover(this, cover, binding.ivVideoCover)
            }
        }

        // 监听输入的标签
        viewModel.selectedTags.observe(this) { tags ->
            // 1. 处理分割线
            binding.tagDivider.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE

            // 2. 清空动态容器
            binding.llTagContainer.removeAllViews()

            // 3. 动态添加
            tags.forEach { tagName ->
                // 使用 ViewBinding 加载我们刚写的简单模板
                val tagBinding = ItemSimpleTagBinding.inflate(layoutInflater, binding.llTagContainer, false)

                tagBinding.root.text = tagName

                // 设置标签间距 (Margin)
                val params = tagBinding.root.layoutParams as LinearLayout.LayoutParams
                params.marginEnd = 8.dp
                tagBinding.root.layoutParams = params

                binding.llTagContainer.addView(tagBinding.root)
            }
        }

    }

    private fun setupVideoPartsList() {
        partAdapter = ReleaseVideoPartAdapter(
            onPartSelected = { part -> viewModel.selectPart(part.id) },
            onMoreClick = { part, anchor ->
                viewModel.selectPart(part.id)
                showPartMenu(part, anchor)
            },
        )
        partItemTouchHelper = ItemTouchHelper(
            ReleaseVideoPartDragCallback(
                partAdapter,
                onMoveSuccess = { from, to ->
                    viewModel.movePart(from, to)
                    scrollToPartIfNeeded(to)
                },
                onDragEnded = {
                    partAdapter.setParts(viewModel.videoParts.value.orEmpty())
                    refreshUploadUi()
                },
            ),
        )
        binding.rvVideoParts.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvVideoParts.adapter = partAdapter
        // 开启移动动画，让分P交换过程更顺滑
        binding.rvVideoParts.itemAnimator = DefaultItemAnimator().apply {
            moveDuration = 140L
            addDuration = 120L
            removeDuration = 120L
            changeDuration = 0L
        }
        partItemTouchHelper.attachToRecyclerView(binding.rvVideoParts)
        binding.rvVideoParts.fixHorizontalScrollConflictWithParent()
    }

    private fun refreshUploadUi() {
        val parts = viewModel.videoParts.value.orEmpty()
        if (parts.isEmpty()) {
            binding.topUploadLayout.visibility = View.GONE
            topProgressAnimator?.cancel()
            binding.progressBarTopUpload.progress = 0
            return
        }
        val topUploadingPart = viewModel.getTopBarUploadPart()
        if (topUploadingPart != null) {
            val partIndex = parts.indexOfFirst { it.id == topUploadingPart.id }
            val partLabel = if (partIndex >= 0) "P${partIndex + 1}" else ""
            if (topProgressPartId != topUploadingPart.id) {
                topProgressPartId = topUploadingPart.id
                lastTopShownProgress = 0
                topProgressAnimator?.cancel()
                binding.progressBarTopUpload.progress = 0
            }
            val progress = maxOf(
                lastTopShownProgress,
                topUploadingPart.uploadProgress.coerceIn(0, 99),
            )
            lastTopShownProgress = progress
            binding.topUploadLayout.visibility = View.VISIBLE
            animateTopProgressTo(progress)
            val labelPrefix = if (partLabel.isNotEmpty()) "$partLabel " else ""
            binding.tvTopUploadStatus.text = "${labelPrefix}上传中 ${progress}%"
        } else {
            topProgressPartId = null
            lastTopShownProgress = 0
            binding.topUploadLayout.visibility = View.GONE
            topProgressAnimator?.cancel()
            binding.progressBarTopUpload.progress = 0
        }
    }

    private fun animateTopProgressTo(target: Int) {
        val bar = binding.progressBarTopUpload
        val safeTarget = target.coerceIn(0, bar.max)
        val start = bar.progress
        if (safeTarget == start) return
        topProgressAnimator?.cancel()
        if (safeTarget < start) return
        topProgressAnimator = ObjectAnimator.ofInt(bar, "progress", start, safeTarget).apply {
            duration = 280L
            start()
        }
    }

    private fun scrollToPartIfNeeded(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        binding.rvVideoParts.post {
            (binding.rvVideoParts.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, 0)
        }
    }

    private fun updateAddPartButtonState() {
        val uploading = viewModel.isAnyPartUploading()
        binding.addShard.isEnabled = !uploading
        binding.addShard.alpha = if (uploading) 0.45f else 1f
    }

    /**
     * 显示分p设置
     */
    private fun showPartMenu(part: ReleaseVideoPart, anchor: View) {
        // 1. 使用 ViewBinding 直接“充气”加载菜单布局
        val menuBinding = LayoutPartMoreMenuBinding.inflate(layoutInflater)
        val contentView = menuBinding.root

        // 2. 强行触发后台测量（宽度死锁 120dp，高度根据内容自动算）
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(160.dp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        // 3. 实例化弹窗，直接用测量好的精确宽高
        val popupWindow = PopupWindow(
            contentView,
            contentView.measuredWidth,
            contentView.measuredHeight,
            true,
        ).apply {
            // 启动点击气泡外会关闭气泡
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8.dp.toFloat()
        }
        popupWindow.setOnDismissListener { hidePartMenuDim() }

        // 替换视频
        menuBinding.actionReplaceVideo.setOnClickListener {
            popupWindow.dismiss()
            pendingPickerAction = PartPickerAction.Replace(part.id)
            PermissionHelper.requestPublishVideo(this) { openPartVideoPicker() }
        }

        // 修改标题
        menuBinding.actionEditTitle.setOnClickListener {
            popupWindow.dismiss()
            PartTitleBottomSheetDialogFragment
                .newInstance(
                    part.id,
                    part.displayTitle.take(resources.getInteger(R.integer.release_part_title_max_length)),
                )
                .show(supportFragmentManager, "PartTitleBottomSheet")
        }

        // 删除该视频
        menuBinding.actionDeletePart.setOnClickListener {
            popupWindow.dismiss()
            if (!viewModel.removePart(part.id)) {
                ToastUtils.showShort(this, getString(R.string.release_part_keep_one))
            }
        }

        // 5. 精准计算坐标并把它贴在三个点按钮下方
        val popupWidth = contentView.measuredWidth
        val xOffset = -(popupWidth - anchor.width - 10.dp)
        showPartMenuDim(popupWindow)
        popupWindow.showAsDropDown(anchor, xOffset, 6.dp)
    }

    /**
     * 给气泡菜单添加全屏变暗遮罩（点击遮罩也可关闭气泡）
     */
    private fun showPartMenuDim(popupWindow: PopupWindow) {
        if (partMenuDimView != null) return
        // 获取根布局容器
        val container = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        // 创建全屏view视图
        val dimView = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#66000000"))
            alpha = 0f
            isClickable = true
            setOnClickListener { popupWindow.dismiss() }
        }
        container.addView(dimView)
        dimView.animate().alpha(1f).setDuration(120L).start()
        partMenuDimView = dimView
    }

    private fun hidePartMenuDim() {
        val dimView = partMenuDimView ?: return
        val parent = dimView.parent as? ViewGroup
        parent?.removeView(dimView)
        partMenuDimView = null
    }

    private fun updateCoverPreview(videoPath: String) {
        try {
            val bitmap = VideoThumbnailUtil.getFirstFrameBitmap(videoPath)
            if (bitmap != null) {
                binding.ivVideoCover.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e("ReleaseVideo", "更新封面预览失败", e)
        }
    }

    private fun extractAndUploadCover(videoPath: String) {
        try {
            // 使用VideoThumbnailUtil获取第一帧并保存为文件
            val coverImagePath = VideoThumbnailUtil.getFirstFrame(this, videoPath)
            if (coverImagePath != null) {
                // 设置视频第一帧为封面显示
                val firstFrameBitmap = VideoThumbnailUtil.getFirstFrameBitmap(videoPath)
                if (firstFrameBitmap != null) {
                    binding.ivVideoCover.setImageBitmap(firstFrameBitmap)
                }
                Log.d("ReleaseVideo", "成功截取视频第一帧: $coverImagePath")

                // 上传封面图片到后端
                uploadCoverImage(coverImagePath)
            }
        } catch (e: Exception) {
            Log.e("ReleaseVideo", "截取视频第一帧失败", e)
        }
    }

    /**
     * 默认封面逻辑：只在首次有分P时初始化一次，后续拖拽/删除不自动改封面
     */
    private fun applyAutoCoverFromFirstPart(parts: List<ReleaseVideoPart>) {
        if (hasCustomCover || hasInitDefaultCover) return
        val firstPartPath = parts.firstOrNull()?.filePath ?: return
        hasInitDefaultCover = true
        extractAndUploadCover(firstPartPath)
    }

    private fun openCoverImagePicker() {
        PictureSelector.create(this)
            .openGallery(SelectMimeType.ofImage())
            .setImageEngine(GlideEngine)
            .setSelectionMode(SelectModeConfig.SINGLE)
            .setMaxSelectNum(1)
            .isDisplayCamera(true)
            .isPreviewVideo(false)
            .isPreviewImage(false)
            .isPreviewAudio(false)
            .forResult(object : OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>?) {
                    val media = result?.getOrNull(0) ?: return
                    val imagePath = media.realPath
                    hasCustomCover = true
                    binding.ivVideoCover.setImageURI(android.net.Uri.parse(imagePath))
                    uploadCoverImage(imagePath)
                }

                override fun onCancel() {
                    Log.d("ReleaseVideo", "取消选择自定义封面")
                }
            })
    }

    /**
     * 上传封面图片到后端
     */
    private fun uploadCoverImage(imagePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileService = RetrofitClient.create(FileService::class.java)
                val imageFile = java.io.File(imagePath)

                // 准备上传参数
                val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("file", imageFile.name, requestBody)
                val createThumbnail = "true".toRequestBody("text/plain".toMediaTypeOrNull())

                Log.d("ReleaseVideo", "开始上传封面图片: ${imageFile.name}")

                // 上传图片
                val response = fileService.postImage(imagePart, createThumbnail)
                Log.d("ReleaseVideo", "封面上传结果: $response")

                // 解析响应获取图片URL（假设后端返回JSON格式）
                try {
                    val responseJson = org.json.JSONObject(response)
                    val imageUrl = responseJson.optString("data", "")
                    val finalUrl = imageUrl.ifEmpty {
                        // 如果无法解析URL，使用默认路径
                        imageFile.name
                    }

                    // 在主线程更新LiveData
                    withContext(Dispatchers.Main) {
                        viewModel.setVideoCoverUrl(finalUrl)
                    }

                    if (imageUrl.isNotEmpty()) {
                        Log.d("ReleaseVideo", "封面URL: $imageUrl")
                    } else {
                        Log.w("ReleaseVideo", "无法解析图片URL，使用文件名")
                    }
                } catch (_: Exception) {
                    // JSON解析失败，使用默认路径
                    // 在主线程更新LiveData
                    withContext(Dispatchers.Main) {
                        viewModel.setVideoCoverUrl(imageFile.name)
                    }
                    Log.w("ReleaseVideo", "响应解析失败，使用文件名: ${imageFile.name}")
                }

            } catch (e: Exception) {
                Log.e("ReleaseVideo", "封面上传失败", e)
                // 上传失败时使用默认封面
                // 在主线程更新LiveData
                withContext(Dispatchers.Main) {
                    viewModel.setVideoCoverUrl("default_cover.jpg")
                }
            }
        }
    }

    private fun showTagsAndPartition() {
        val tagSheet = TagBottomSheetDialogFragment()

        Log.e("error","点击")
        // 使用 supportFragmentManager 弹出它
        tagSheet.show(supportFragmentManager, "TagSheet")
    }

    private fun openPartVideoPicker() {
        PictureSelector.create(this)
            .openGallery(SelectMimeType.ofVideo())
            .setImageEngine(GlideEngine)
            .setSelectionMode(SelectModeConfig.SINGLE)
            .setMaxSelectNum(1)
            .isDisplayCamera(true)
            .isPreviewVideo(false)
            .isPreviewImage(false)
            .isPreviewAudio(false)
            .forResult(object : OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>?) {
                    val media = result?.getOrNull(0) ?: return
                    val videoPath = media.realPath
                    val duration = media.duration
                    when (val action = pendingPickerAction) {
                        PartPickerAction.Add -> {
                            viewModel.addPart(videoPath, duration)
                        }
                        is PartPickerAction.Replace -> {
                            viewModel.replacePart(action.partId, videoPath, duration)
                        }
                    }
                }

                override fun onCancel() {
                    Log.d("ReleaseVideo", "取消添加分P视频")
                }
            })
    }

    private fun publishVideo() {
        viewModel.postVideo()
    }

    /** 灰色仅作提示，按钮保持可点以便弹出原因 Toast（disabled 的 Button 收不到点击） */
    private fun updatePublishButtonVisual() {
        val ready = viewModel.isPublishReady()
        binding.btnPublish.isEnabled = true
        binding.btnPublish.isClickable = true
        binding.btnPublish.alpha = if (ready) 1f else 0.5f
        Log.d("ReleaseVideo", "publishReady=$ready, ${viewModel.publishBlockReason()}")
    }

    override fun onResume() {
        super.onResume()
        updatePublishButtonVisual()
    }

    private fun applyEditModeUi() {
        val editing = viewModel.isEditMode()
        binding.tvToolbarTitle.setText(
            if (editing) R.string.release_title_edit else R.string.release_title_publish,
        )
        binding.btnPublish.setText(
            if (editing) R.string.release_btn_save else R.string.publish,
        )
    }

    private fun requestExitPage() {
        if (viewModel.shouldRetainUploadsOnServer()) {
            finish()
            return
        }
        if (viewModel.isEditMode()) {
            AlertDialog.Builder(this, R.style.PinkDialogTheme)
                .setMessage(R.string.release_exit_edit_confirm)
                .setPositiveButton(R.string.confirm) { _, _ -> finish() }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val hasUploads = viewModel.videoParts.value.orEmpty().any { it.uploadId.isNotEmpty() }
        if (!hasUploads) {
            finish()
            return
        }
        AlertDialog.Builder(this, R.style.PinkDialogTheme)
            .setMessage(R.string.release_exit_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteAllUploads()
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupPublishAgreement() {
        val prefix = getString(R.string.publish_protocol_prefix)
        val link = getString(R.string.community_convention_link)
        val full = prefix + link
        val spannable = SpannableString(full)
        val linkStart = prefix.length
        val linkEnd = full.length
        val pink = ContextCompat.getColor(this, R.color.bili_pink)
        spannable.setSpan(
            ForegroundColorSpan(pink),
            linkStart,
            linkEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    CommunityConventionActivity.start(this@ReleaseVideoActivity)
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = pink
                    ds.isUnderlineText = false
                }
            },
            linkStart,
            linkEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        binding.tvAgreement.text = spannable
        binding.tvAgreement.movementMethod = LinkMovementMethod.getInstance()
        binding.tvAgreement.highlightColor = Color.TRANSPARENT
    }

    /**
     * 显示用户协议弹窗
     */
    private fun showAgreementDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogAgreementBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.show()

        // show 之后再调整窗口属性
        dialog.window?.apply {
            // 设置背景透明（防止出现系统默认的白边框）
            setBackgroundDrawableResource(android.R.color.transparent)

            // 获取当前窗口参数
            val params = attributes
            // 将宽度设置为屏幕宽度的 85%
            val displayMetrics = resources.displayMetrics
            params.width = (displayMetrics.widthPixels * 0.85).toInt()
            params.height = WindowManager.LayoutParams.WRAP_CONTENT

            // 重新赋值回窗口
            attributes = params
        }

        dialogBinding.btnDialogAgree.setOnClickListener {
            binding.checkboxAgreement.isChecked = true
            dialog.dismiss()
            publishVideo()
        }

        dialogBinding.tvDialogDisagree.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
        }

    }

}