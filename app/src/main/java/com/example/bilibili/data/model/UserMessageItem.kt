package com.example.bilibili.data.model

import org.json.JSONObject

data class UserMessageItem(
    val messageId: Int,
    val messageType: Int,
    val sendUserId: String,
    val sendUserName: String,
    val sendUserAvatar: String,
    val videoId: String,
    val videoName: String,
    val videoCover: String,
    val messageContent: String,
    val messageContentReply: String,
    val commentId: Int,
    val previewType: String,
    val createTimeRaw: String,
    val readType: Int,
) {
    val isCommentTarget: Boolean
        get() = when (previewType) {
            "video" -> false
            "comment" -> true
            else -> commentId > 0 || messageContentReply.isNotBlank()
        }

    /** 用户直接在视频下评论（非回复某条评论） */
    val isDirectVideoComment: Boolean
        get() = messageType == MESSAGE_TYPE_COMMENT &&
            messageContentReply.isBlank() &&
            !isCommentTarget

    fun previewTextForLike(): String =
        messageContentReply.ifBlank { messageContent }.ifBlank { videoName }

    companion object {
        private const val MESSAGE_TYPE_COMMENT = 4

        fun fromJson(json: JSONObject): UserMessageItem {
            val extend = json.optJSONObject("extendDto")
                ?: run {
                    val extendJson = json.optString("extendJson", "")
                    if (extendJson.isEmpty()) JSONObject() else JSONObject(extendJson)
                }
            return UserMessageItem(
                messageId = json.optInt("messageId"),
                messageType = json.optInt("messageType"),
                sendUserId = json.optCleanString("sendUserId"),
                sendUserName = json.optCleanString("sendUserName"),
                sendUserAvatar = json.optCleanString("sendUserAvatar"),
                videoId = json.optCleanString("videoId"),
                videoName = json.optCleanString("videoName"),
                videoCover = json.optCleanString("videoCover"),
                messageContent = extend.optCleanString("messageContent"),
                messageContentReply = extend.optCleanString("messageContentReply"),
                commentId = extend.optInt("commentId", 0),
                previewType = extend.optCleanString("previewType"),
                createTimeRaw = json.optCleanString("createTime"),
                readType = json.optInt("readType", 0),
            )
        }

        private fun JSONObject.optCleanString(key: String): String {
            if (!has(key) || isNull(key)) return ""
            val value = optString(key, "").trim()
            return if (value.equals("null", ignoreCase = true)) "" else value
        }
    }
}
