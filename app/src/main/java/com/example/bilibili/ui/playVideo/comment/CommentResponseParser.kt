package com.example.bilibili.ui.playVideo.comment

import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.data.model.CommentDataContainer
import com.google.gson.Gson
import org.json.JSONObject

object CommentResponseParser {

    private val gson = Gson()

    fun parsePage(jsonString: String): CommentPageResult? {
        val root = JSONObject(jsonString)
        if (root.optInt("code", -1) != 200) return null
        val dataObj = root.optJSONObject("data") ?: return null
        val container = gson.fromJson(dataObj.toString(), CommentDataContainer::class.java)
        val pager = container.commentData
        val comments = pager.list.orEmpty()
        val actionMap = container.userActionList.orEmpty()
            .associateBy({ it.commentId }, { it.actionType })
        comments.forEach { applyUserActions(it, actionMap) }
        return CommentPageResult(
            comments = comments,
            pageNo = pager.pageNo,
            pageTotal = pager.pageTotal,
            pageSize = pager.pageSize,
            totalCount = pager.totalCount,
        )
    }

    private fun applyUserActions(item: CommentItem, actionMap: Map<Int, Int>) {
        val type = actionMap[item.commentId]
        item.isLiked = type == 0
        item.isHated = type == 1
        item.children?.forEach { child -> applyUserActions(child, actionMap) }
    }

    data class CommentPageResult(
        val comments: List<CommentItem>,
        val pageNo: Int,
        val pageTotal: Int,
        val pageSize: Int,
        val totalCount: Int,
    ) {
        val hasMore: Boolean
            get() = pageNo < pageTotal
    }
}
