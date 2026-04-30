package com.example.bilibili.data.model

/**
 * 用户操作记录
 */
data class UserAction(
    val actionId: Int,
    val videoId: String,
    val videoUserId: String,
    val commentId: Int,
    val actionType: Int, // 通常 0:点赞, 1:踩
    val actionCount: Int,
    val userId: String,
    val actionTime: String
)