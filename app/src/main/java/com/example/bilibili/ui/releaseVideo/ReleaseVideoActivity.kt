package com.example.bilibili.ui.releaseVideo

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.bilibili.data.api.FileService
import com.example.bilibili.databinding.ActivityReleaseVideoBinding
import com.example.bilibili.databinding.DialogAgreementBinding
import com.example.bilibili.databinding.ItemSimpleTagBinding
import com.example.bilibili.ui.releaseVideo.bottomSheet.AuthorStatementBottomSheetDialogFragment
import com.example.bilibili.ui.releaseVideo.bottomSheet.IntroductionBottomSheetDialogFragment
import com.example.bilibili.ui.releaseVideo.bottomSheet.TagBottomSheetDialogFragment
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import com.example.bilibili.util.VideoThumbnailUtil
import com.example.bilibili.util.UiUtils.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class ReleaseVideoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReleaseVideoBinding

    private val viewModel: ReleaseVideoViewModel by viewModels()

    @SuppressLint("SetTextI18n")
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityReleaseVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 监听返回按钮点击事件
        binding.btnBack.setOnClickListener {
            // 如果已经上传了文件，询问是否删除
            val uploadInfo = viewModel.videoUploadInfo.value
            if (uploadInfo != null && uploadInfo.uploadId.isNotEmpty()) {
                // 已上传文件，自动删除
                viewModel.deleteUpload(uploadInfo.uploadId)
                Log.d("ReleaseVideo", "删除已上传的视频文件")
            }
            // 退出页面
            finish()
        }

        // 获取从 MainActivity 传递过来的视频信息
        val videoPath = intent.getStringExtra("video_path")
        val videoDuration = intent.getLongExtra("video_duration", 0)

        if (videoPath != null && videoDuration > 0) {
            // 设置视频信息到 ViewModel
            viewModel.setVideoInfo(videoPath, videoDuration)
            Log.d("ReleaseVideo", "接收视频信息: 路径=$videoPath, 时长=${videoDuration/1000}秒")

            // 1. 截取视频第一帧作为封面
            extractVideoFirstFrame(videoPath)

            // 2. 自动开始上传视频
            startAutoUpload()
        }

        // 添加分片功能，即可以添加上传更多的视频
        binding.addShard.setOnClickListener {

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

        // 监听发布按钮点击事件
        binding.btnPublish.setOnClickListener {
            // 检查是否同意了协议
            if (binding.checkboxAgreement.isChecked) {
                // 已同意，调用投稿方法
                viewModel.postVideo()
            } else {
                // 未同意，显示协议对话框
                showAgreementDialog()
            }
        }

        // 初始状态下禁用投稿按钮，直到上传完成
        binding.btnPublish.isEnabled = false
        binding.btnPublish.alpha = 0.5f

        // 监听投稿状态
        viewModel.postStatus.observe(this) { status ->
            when (status) {
                "投稿成功" -> {
                    // 投稿成功，显示成功提示并返回
                    ToastUtils.showShort(this, "投稿成功")
                    Log.d("ReleaseVideo", "投稿成功")
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
            binding.partition.text = item?.categoryName
        }

        // 监听创作声明类型变化
        viewModel.statementType.observe(this) { type ->
            val text = when (type) {
                ReleaseVideoViewModel.StatementType.ORIGINAL -> "视频自制"
                ReleaseVideoViewModel.StatementType.REPOST -> "视频转载"
            }
            binding.reprintOrMakeYourOwn.text = text
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

        // 监听视频上传进度
        viewModel.uploadProgress.observe(this) { progress ->
            binding.progressBarUpload.progress = progress
            binding.tvUploadProgress.text = "${progress}%"
            Log.d("ReleaseVideo", "上传进度: $progress%")
        }

        // 监听上传状态
        viewModel.uploadStatus.observe(this) { status ->
            binding.tvUploadStatus.text = status

            // 根据状态显示/隐藏进度条和控制投稿按钮
            when (status) {
                "正在初始化上传...", "正在上传分片 1/1", "正在上传分片 1/2" -> {
                    binding.uploadProgressLayout.visibility = View.VISIBLE
                    binding.btnPublish.isEnabled = false
                    binding.btnPublish.alpha = 0.5f
                }
                "上传完成" -> {
                    binding.uploadProgressLayout.visibility = View.VISIBLE
                    binding.progressBarUpload.progress = 100
                    binding.tvUploadProgress.text = "100%"
                    binding.btnPublish.isEnabled = true
                    binding.btnPublish.alpha = 1.0f
                }
                "预上传失败", "上传失败", "分片 1 上传失败" -> {
                    binding.uploadProgressLayout.visibility = View.VISIBLE
                    binding.btnPublish.isEnabled = false
                    binding.btnPublish.alpha = 0.5f
                }
                else -> {
                    // 其他状态保持当前显示状态
                    // 默认禁用投稿按钮，直到上传完成
                    binding.btnPublish.isEnabled = false
                    binding.btnPublish.alpha = 0.5f
                }
            }

            Log.d("ReleaseVideo", "上传状态: $status")
        }
    }

    /**
     * 截取视频第一帧作为封面
     */
    private fun extractVideoFirstFrame(videoPath: String) {
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

    /**
     * 自动开始上传视频
     */
    private fun startAutoUpload() {
        // 延迟1秒后开始上传，给用户一些时间看到界面
        binding.root.postDelayed({
            viewModel.startVideoUpload()
            Log.d("ReleaseVideo", "开始自动上传视频")
        }, 1000)
    }

    private fun showTagsAndPartition() {
        val tagSheet = TagBottomSheetDialogFragment()

        Log.e("error","点击")
        // 使用 supportFragmentManager 弹出它
        tagSheet.show(supportFragmentManager, "TagSheet")
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
        }

        dialogBinding.tvDialogDisagree.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
        }

    }

}