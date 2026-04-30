package com.example.bilibili.data.model

data class CommentPager(
    val totalCount: Int,
    val pageSize: Int,
    val pageNo: Int,
    val pageTotal: Int,
    val list: List<CommentItem>
)