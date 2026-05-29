package com.example.bilibili.data.model

data class CreatorCommentItem(
    val commentId: Int,
    val content: String,
    val videoId: String,
    val videoName: String,
    val userId: String,
    val nickName: String,
    val postTime: String,
    val avatar: String = "",
    val videoCover: String = "",
)
