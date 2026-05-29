package com.example.bilibili.ui.message

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.ui.user.UserProfileActivity
import com.example.bilibili.util.ToastUtils

object MessageNavigator {

    fun bindUserProfileClick(view: View, userId: String, onOpen: () -> Unit) {
        view.isClickable = true
        view.setOnClickListener {
            if (userId.isNotBlank()) {
                onOpen()
            }
        }
    }

    fun openUserProfile(context: Context, userId: String) {
        if (userId.isBlank()) {
            ToastUtils.showShort(context, "用户信息不可用")
            return
        }
        context.startActivity(
            Intent(context, UserProfileActivity::class.java).apply {
                putExtra("user_id", userId)
            },
        )
    }

    fun openMessageTarget(context: Context, item: UserMessageItem) {
        when (item.messageType) {
            MessageTypes.COMMENT -> MessageCommentNavigator.openFromMessage(context, item)
            MessageTypes.LIKE, MessageTypes.COLLECTION -> openVideo(context, item.videoId)
            MessageTypes.FANS -> openUserProfile(context, item.sendUserId)
            else -> {
                if (item.videoId.isNotBlank()) {
                    openVideo(context, item.videoId)
                }
            }
        }
    }

    fun openVideo(context: Context, videoId: String) {
        if (videoId.isBlank()) {
            ToastUtils.showShort(context, "视频信息不可用")
            return
        }
        val intent = Intent(context, PlayVideoActivity::class.java).apply {
            putExtra(PlayVideoActivity.EXTRA_VIDEO_ID, videoId)
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
