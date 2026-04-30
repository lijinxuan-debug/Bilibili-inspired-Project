package com.example.bilibili.data.model

data class CommentDataContainer(
    val commentData: CommentPager,
    val userActionList: List<UserAction>
)