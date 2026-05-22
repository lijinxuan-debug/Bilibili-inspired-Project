package com.example.bilibili.data.model

/**
 * 核心评论对象（支持无限级嵌套）
 */
data class CommentItem(
    val commentId: Int,
    val pCommentId: Int,
    val videoId: String,
    val videoUserId: String,
    val content: String,
    val imgPath: String?,
    val userId: String,
    val replyUserId: String?,
    val topType: Int,
    val postTime: String,
    var likeCount: Int,
    var hateCount: Int,
    val avatar: String,
    val nickName: String,
    val replyAvatar: String?,
    val replyNickName: String?,
    
    // 关键：children 里的对象类型和 CommentItem 本身一致
    // 使用 List? 是因为 JSON 中该字段可能为 null 或空数组 []
    val children: List<CommentItem>?,

    var isLiked: Boolean = false,
    var isHated: Boolean = false
)