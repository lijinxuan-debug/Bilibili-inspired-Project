package com.example.bilibili.data.model

// 专门用来映射后端返回的雪碧图配置
data class PreviewConfigEntity(
    val url: String = "",
    val total: Int = 400,
    val col: Int = 10,
    val row: Int = 40,
    val frameW: Int = 160,
    val frameH: Int = 90,
    val interval: Double = 0.0
)