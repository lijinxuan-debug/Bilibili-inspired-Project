package com.example.bilibili.ui.message

import android.content.Context
import com.example.bilibili.R
import com.example.bilibili.data.model.UserMessageItem

object MessagePreviewFormatter {

    fun preview(context: Context, item: UserMessageItem): String {
        return when (item.messageType) {
            MessageTypes.FANS -> context.getString(R.string.message_action_followed_me).trim()
            MessageTypes.LIKE -> if (item.isCommentTarget) {
                val comment = item.previewTextForLike()
                context.getString(R.string.message_liked_my_comment) +
                    if (comment.isNotBlank()) "：$comment" else ""
            } else {
                context.getString(R.string.message_liked_my_video) +
                    item.videoName.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            }
            MessageTypes.COLLECTION -> context.getString(R.string.message_collected_my_video) +
                item.videoName.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            MessageTypes.COMMENT -> {
                if (item.isDirectVideoComment) {
                    val body = item.messageContent
                    context.getString(R.string.message_commented_my_video) +
                        if (body.isNotBlank()) "：$body" else ""
                } else {
                    val body = item.messageContent.ifBlank { item.messageContentReply }
                    context.getString(R.string.message_action_reply_comment) +
                        if (body.isNotBlank()) "：$body" else ""
                }
            }
            else -> item.messageContent.ifBlank { item.videoName }
        }
    }
}
