package com.example.bilibili.data.model

data class DanmuEntity(
    var danmuId: Int = 0,               // 加上 = 0
    var videoId: String = "",           // 加上 = ""
    var fileId: String = "",            // 加上 = ""
    var userId: String = "",            // 加上 = ""
    var postTime: String = "",          // 加上 = ""
    var text: String = "",              // 加上 = ""
    var mode: Int = 0,                  // 加上 = 0
    var color: String = "#FFFFFF",      // 加上默认颜色
    var time: Int = 0,                  // 加上 = 0
    var videoName: String? = null,      // 加上 = null
    var videoCover: String? = null,     // 加上 = null
    var nickName: String? = ""          // 加上 = ""
)