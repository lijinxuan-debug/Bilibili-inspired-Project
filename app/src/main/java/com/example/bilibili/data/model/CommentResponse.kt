package com.example.bilibili.data.model

data class CommentResponse(
    val status: String,
    val code: Int,
    val message: String,
    val data: CommentDataContainer
)