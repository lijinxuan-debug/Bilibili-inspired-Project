package com.example.bilibili.ui.message

import com.example.bilibili.data.api.MessageService
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.util.ApiJson.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class MessageQuickBadges(
    val reply: Int = 0,
    val like: Int = 0,
    val fans: Int = 0,
)

object MessageUnreadCenter {

    private val _badges = MutableStateFlow(MessageQuickBadges())
    val badges: StateFlow<MessageQuickBadges> = _badges.asStateFlow()

    private val locallyReadMessageIds = ConcurrentHashMap.newKeySet<Int>()

    var onInboxShouldRefresh: (() -> Unit)? = null

    suspend fun refreshFromApi(messageService: MessageService) {
        val response = JSONObject(messageService.getNoReadCountGroup())
        if (!response.isSuccess()) return
        applyCounts(response.optJSONArray("data"))
    }

    fun applyCounts(counts: JSONArray?) {
        var reply = 0
        var like = 0
        var fans = 0
        if (counts != null) {
            for (i in 0 until counts.length()) {
                val row = counts.optJSONObject(i) ?: continue
                val type = row.optInt("messageType")
                val count = row.optInt("messageCount")
                when (type) {
                    MessageTypes.COMMENT -> reply = count
                    MessageTypes.LIKE, MessageTypes.COLLECTION -> like += count
                    MessageTypes.FANS -> fans = count
                }
            }
        }
        _badges.value = MessageQuickBadges(reply = reply, like = like, fans = fans)
    }

    fun applySsePayload(json: JSONObject) {
        when (json.optString("event")) {
            "sync" -> applyCounts(json.optJSONArray("counts"))
            "message" -> onInboxShouldRefresh?.invoke()
        }
    }

    fun clearReply() {
        _badges.update { it.copy(reply = 0) }
    }

    fun clearLike() {
        _badges.update { it.copy(like = 0) }
    }

    fun clearFans() {
        _badges.update { it.copy(fans = 0) }
    }

    fun clearAll() {
        _badges.value = MessageQuickBadges()
        locallyReadMessageIds.clear()
    }

    fun markMessageReadLocally(messageId: Int) {
        if (messageId > 0) {
            locallyReadMessageIds.add(messageId)
        }
    }

    fun isMessageUnread(item: UserMessageItem): Boolean {
        if (locallyReadMessageIds.contains(item.messageId)) return false
        return item.readType == 0
    }
}
