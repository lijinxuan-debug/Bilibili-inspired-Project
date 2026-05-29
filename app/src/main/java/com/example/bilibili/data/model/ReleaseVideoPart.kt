package com.example.bilibili.data.model

import java.util.UUID

data class ReleaseVideoPart(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val fileName: String,
    val duration: Long,
    var displayTitle: String,
    var uploadId: String = "",
    var uploadProgress: Int = 0,
    var uploadStatus: String = "",
    /** 服务端已有分P的 fileId，编辑保存时必须回传，否则会被当成新增而重复入库 */
    val serverFileId: String = "",
    val persistedFileSize: Long = 0L,
    /** 0 转码中 1 成功 2 失败；-1 表示本地新上传尚未入库 */
    val transferResult: Int = -1,
) {
    val isPersistedOnServer: Boolean
        get() = serverFileId.isNotBlank()
    val partLabel: String
        get() = "P$displayIndex"

    var displayIndex: Int = 1

    fun shortFileName(maxLen: Int = 14): String {
        val name = displayTitle.ifBlank { fileName }
        return if (name.length <= maxLen) name else name.take(maxLen) + "..."
    }
}
