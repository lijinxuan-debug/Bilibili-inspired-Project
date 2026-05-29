package com.example.bilibili.data.model

/**
 * 视频文件信息 - 用于投稿接口
 */
data class VideoInfoFilePost(
    val fileId: String? = null,        // 已有分P ID（编辑时必传）
    val uploadId: String = "",         // 上传ID
    val fileName: String = "",         // 分P标题（对应服务端 fileName）
    val fileSize: Long = 0L,           // 文件大小
    val duration: Int = 0,             // 视频时长（秒）
    val fileMd5: String = ""           // 文件MD5（可选）
)