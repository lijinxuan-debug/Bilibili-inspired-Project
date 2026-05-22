package com.example.bilibili.ui.playVideo.comment

import com.example.bilibili.data.model.CommentItem
import kotlin.math.max

/** 评论点赞/踩的乐观 UI 更新（同步数量与互斥状态） */
object CommentActionHelper {

    fun applyLikeToggle(item: CommentItem) {
        val wasLiked = item.isLiked
        val wasHated = item.isHated
        if (wasLiked) {
            item.isLiked = false
            item.likeCount = max(0, item.likeCount - 1)
        } else {
            item.isLiked = true
            item.likeCount += 1
            if (wasHated) {
                item.isHated = false
                item.hateCount = max(0, item.hateCount - 1)
            }
        }
    }

    fun applyDislikeToggle(item: CommentItem) {
        val wasHated = item.isHated
        val wasLiked = item.isLiked
        if (wasHated) {
            item.isHated = false
            item.hateCount = max(0, item.hateCount - 1)
        } else {
            item.isHated = true
            item.hateCount += 1
            if (wasLiked) {
                item.isLiked = false
                item.likeCount = max(0, item.likeCount - 1)
            }
        }
    }

    fun revertLikeToggle(item: CommentItem, wasLiked: Boolean, wasHated: Boolean, oldLikeCount: Int, oldHateCount: Int) {
        item.isLiked = wasLiked
        item.isHated = wasHated
        item.likeCount = oldLikeCount
        item.hateCount = oldHateCount
    }

    fun revertDislikeToggle(item: CommentItem, wasLiked: Boolean, wasHated: Boolean, oldLikeCount: Int, oldHateCount: Int) {
        item.isLiked = wasLiked
        item.isHated = wasHated
        item.likeCount = oldLikeCount
        item.hateCount = oldHateCount
    }
}
