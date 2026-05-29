package com.example.bilibili.ui.message

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.ui.playVideo.comment.CommentReplyActivity
import com.example.bilibili.util.ToastUtils

object MessageCommentNavigator {

    fun openFromMessage(context: Context, item: UserMessageItem) {
        if (item.messageType != MessageTypes.COMMENT) return
        if (item.videoId.isBlank()) {
            ToastUtils.showShort(context, "视频信息不可用")
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            ToastUtils.showShort(context, "无法打开评论区")
            return
        }
        if (item.isDirectVideoComment || item.commentId <= 0) {
            openVideoCommentTab(activity, item)
        } else {
            CommentReplyActivity.startFromMessage(activity, item)
        }
    }

    private fun openVideoCommentTab(activity: Activity, item: UserMessageItem) {
        activity.startActivity(
            Intent(activity, PlayVideoActivity::class.java).apply {
                putExtra("video_id", item.videoId)
                putExtra(PlayVideoActivity.EXTRA_OPEN_COMMENT_TAB, true)
                putExtra(PlayVideoActivity.EXTRA_ANCHOR_SEND_USER_ID, item.sendUserId)
                putExtra(PlayVideoActivity.EXTRA_ANCHOR_CONTENT, item.messageContent)
            },
        )
    }
}
