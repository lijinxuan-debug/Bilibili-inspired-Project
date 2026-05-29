package com.example.bilibili.data.model

data class CreatorDanmuItem(
    val danmuId: Int,
    val text: String,
    val videoId: String,
    val videoName: String,
    val userId: String,
    val nickName: String,
    val postTime: String,
    val avatar: String = "",
    val videoCover: String = "",
    /** 弹幕出现在视频中的时间点（秒） */
    val playTimeSec: Int = 0,
)
