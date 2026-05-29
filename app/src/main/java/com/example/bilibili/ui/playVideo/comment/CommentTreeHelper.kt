package com.example.bilibili.ui.playVideo.comment

import com.example.bilibili.data.model.CommentItem

object CommentTreeHelper {

    fun findById(comments: List<CommentItem>, commentId: Int): CommentItem? {
        for (comment in comments) {
            if (comment.commentId == commentId) return comment
            val nested = comment.children?.let { findById(it, commentId) }
            if (nested != null) return nested
        }
        return null
    }

    /**
     * 获取某条评论下的直接回复。优先用 children，再按 pCommentId 在整棵树里兜底。
     */
    fun directReplies(allRoots: List<CommentItem>, parentCommentId: Int): List<CommentItem> {
        val parent = findById(allRoots, parentCommentId)
        val fromChildren = parent?.children.orEmpty()
        if (fromChildren.isNotEmpty()) return fromChildren
        return collectByParentId(allRoots, parentCommentId)
    }

    private fun collectByParentId(comments: List<CommentItem>, parentCommentId: Int): List<CommentItem> {
        val result = mutableListOf<CommentItem>()
        fun walk(nodes: List<CommentItem>) {
            for (node in nodes) {
                if (node.pCommentId == parentCommentId) {
                    result.add(node)
                }
                node.children?.let { walk(it) }
            }
        }
        walk(comments)
        return result
    }
}
