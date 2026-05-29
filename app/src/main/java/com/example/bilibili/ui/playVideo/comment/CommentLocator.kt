package com.example.bilibili.ui.playVideo.comment

import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.ui.playVideo.CommentAnchor
import com.example.bilibili.ui.playVideo.matches

object CommentLocator {

    data class LocateResult(
        val highlightCommentId: Int,
        val expandParentCommentId: Int? = null,
    )

    fun findInList(list: List<CommentItem>, anchor: CommentAnchor): LocateResult? {
        for (item in list) {
            if (anchor.matches(item.userId, item.content)) {
                return LocateResult(highlightCommentId = item.commentId)
            }
            val children = item.children.orEmpty()
            for (child in children) {
                if (anchor.matches(child.userId, child.content)) {
                    return LocateResult(
                        highlightCommentId = child.commentId,
                        expandParentCommentId = item.commentId,
                    )
                }
            }
        }
        return null
    }

    fun findInFlatList(list: List<CommentItem>, anchor: CommentAnchor): LocateResult? {
        for (item in list) {
            if (anchor.matches(item.userId, item.content)) {
                return LocateResult(highlightCommentId = item.commentId)
            }
        }
        return null
    }
}
