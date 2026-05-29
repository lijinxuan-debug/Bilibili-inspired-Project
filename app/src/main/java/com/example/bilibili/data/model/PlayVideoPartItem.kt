package com.example.bilibili.data.model

data class PlayVideoPartItem(
    val fileId: String,
    val fileName: String,
    val fileIndex: Int,
    val duration: Int = 0,
) {
    fun displayTitle(): String = fileName.ifBlank { "P$fileIndex" }
}
