package com.example.bilibili.util

import androidx.paging.PagingConfig
import org.json.JSONObject

object PagingDefaults {
    /** 与后端列表接口 pageSize 保持一致 */
    const val PAGE_SIZE = 15
    const val PREFETCH_DISTANCE = 4

    fun videoListConfig(): PagingConfig = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = PREFETCH_DISTANCE,
        enablePlaceholders = false
    )

    /** 根据后端 data 中的 pageNo / pageTotal 计算下一页 */
    fun nextPageKey(dataObject: JSONObject, currentPage: Int): Int? {
        val pageNo = dataObject.optInt("pageNo", currentPage)
        val pageTotal = dataObject.optInt("pageTotal", 1)
        return if (pageNo < pageTotal) pageNo + 1 else null
    }
}
