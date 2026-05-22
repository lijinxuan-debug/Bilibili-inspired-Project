package com.example.bilibili.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import java.io.File
import androidx.appcompat.app.AppCompatActivity // 确保导入
import com.bumptech.glide.Glide
import com.example.bilibili.R
import com.luck.picture.lib.engine.ImageEngine

object GlideEngine : ImageEngine {

    // 检查 Context 是否可用，防止崩溃
    private fun isContextValid(context: Context?): Boolean {
        if (context == null) return false
        if (context is AppCompatActivity) {
            return !context.isFinishing && !context.isDestroyed
        }
        return true
    }

    override fun loadImage(context: Context, url: String, imageView: ImageView) {
        if (!isContextValid(context)) return
        Glide.with(context).load(url).into(imageView)
    }

    override fun loadImage(
        context: Context?,
        imageView: ImageView?,
        url: String?,
        maxWidth: Int,
        maxHeight: Int
    ) {
        if (!isContextValid(context) || imageView == null || url == null) return
        try {
            Glide.with(context!!)
                .load(url)
                .override(maxWidth, maxHeight)
                .centerCrop()
                .placeholder(R.drawable.ic_bili_placeholder)
                .error(R.drawable.ic_bili_placeholder)
                .into(imageView)
        } catch (e: Exception) {
            Log.e("GlideEngine", "加载图片失败: $url", e)
        }
    }

    override fun loadAlbumCover(context: Context, url: String, imageView: ImageView) {
        if (!isContextValid(context)) return
        try {
            Glide.with(context)
                .asBitmap()
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.ic_bili_placeholder)
                .error(R.drawable.ic_bili_placeholder)
                .into(imageView)
        } catch (e: Exception) {
            Log.e("GlideEngine", "加载专辑封面失败: $url", e)
        }
    }

    // 假设这是 GlideEngine.kt 里的代码
    override fun loadGridImage(context: Context, url: String, imageView: ImageView) {
        Glide.with(context)
            .load(url)
            .override(200, 200) // 缩略图优化
            .centerCrop()
            .placeholder(R.drawable.ic_bili_placeholder)
            .error(R.drawable.ic_bili_placeholder)
            .into(imageView)
    }

    override fun pauseRequests(context: Context) {
        if (!isContextValid(context)) return
        Glide.with(context).pauseRequests()
    }

    override fun resumeRequests(context: Context) {
        if (!isContextValid(context)) return
        Glide.with(context).resumeRequests()
    }

    /**
     * 加载用户头像
     * @param avatarPath 服务端相对路径、http(s) URL、本地绝对路径或 content://
     */
    fun loadUserAvatar(context: Context, avatarPath: String?, imageView: ImageView) {
        if (!isContextValid(context)) return

        val path = UserInfoText.normalize(avatarPath)
        val model: Any? = resolveAvatarModel(path)
        Glide.with(context)
            .load(model)
            .placeholder(R.drawable.ic_avatar_default)
            .error(R.drawable.ic_avatar_default)
            .circleCrop()
            .into(imageView)
    }

    /** 相册选图后的本地预览，勿走 getImage 接口 */
    private fun resolveAvatarModel(path: String): Any? {
        if (path.isEmpty()) return R.drawable.ic_avatar_default
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("content://")) return Uri.parse(path)
        if (path.startsWith("/")) {
            val file = File(path)
            if (file.exists()) return file
        }
        return "${RetrofitClient.BASE_URL}file/getImage?sourceName=$path"
    }

    /**
     * 加载视频封面
     * @param coverPath 封面路径
     * @param cornerRadius 圆角大小，单位为 px（例如 15px）
     */
    fun loadVideoCover(context: Context, coverPath: String, imageView: ImageView, cornerRadius: Int = 20) {
        if (!isContextValid(context)) return

        // 同样拼接 Base URL，注意检查参数名是否也是 sourceName
        val fullUrl = "${RetrofitClient.BASE_URL}file/getImage?sourceName=$coverPath"

        Glide.with(context)
            .load(fullUrl)
            // 1. 占位图换成长方形的
            .placeholder(R.drawable.ic_bili_placeholder)
            // 2. 居中裁剪，防止图片比例不对时产生拉伸
            .centerCrop()
            // 3. 设置圆角（而不是圆形）
            .transform(com.bumptech.glide.load.resource.bitmap.RoundedCorners(cornerRadius))
            // 4. 渐变动画，加载出来的时候更丝滑
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
            .into(imageView)
    }

    /**
     * 加载评论图片
     * @param imagePath 图片路径
     * @param imageView 目标ImageView
     */
    fun loadCommentImage(context: Context, imagePath: String, imageView: ImageView) {
        if (!isContextValid(context)) return

        // 拼接完整的图片URL
        val fullUrl = "${RetrofitClient.BASE_URL}file/getImage?sourceName=$imagePath"

        Glide.with(context)
            .load(fullUrl)
            .placeholder(R.drawable.ic_bili_placeholder)
            .error(R.drawable.ic_bili_placeholder)
            .fitCenter()
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
            .into(imageView)
    }

    /**
     * 加载预览图
     */

    /**
     * 创建 GlideEngine 实例，供 PictureSelector 使用
     */
    fun createGlideImageEngine(): ImageEngine {
        return this
    }

}