package com.example.bilibili.data.model

data class VideoItem(
    val videoId: String = "",
    val videoName: String = "",
    val videoCover: String = "",
    val nickName: String = "",
    val playCount: Int = 0,
    val danmuCount: Int = 0,
    // 以下字段在 JSON 中当前为 null 或可能缺失，建议设为可空并给默认值
    val commentCount: Int? = 0,
    val duration: Int? = 0,
    val createTime: String? = ""
)