package com.example.bilibili.ui.playVideo

/**
 * 从消息页跳转到评论区时的定位信息。
 */
data class CommentAnchor(
    val sendUserId: String,
    val content: String,
    val parentCommentId: Int = 0,
)

fun CommentAnchor.matches(userId: String, commentContent: String): Boolean {
    if (sendUserId.isNotBlank() && sendUserId != userId) return false
    if (content.isBlank()) return true
    return commentContent.trim() == content.trim()
}
