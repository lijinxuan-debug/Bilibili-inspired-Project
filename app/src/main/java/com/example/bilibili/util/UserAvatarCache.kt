package com.example.bilibili.util

import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.model.CreatorDanmuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 弹幕列表接口未返回 avatar 时，按 userId 调用 getUserInfo 补全头像并缓存。
 */
object UserAvatarCache {

    private val cache = ConcurrentHashMap<String, String>()
    private val postService by lazy { RetrofitClient.create(PostService::class.java) }

    suspend fun resolveAvatar(userId: String): String {
        val id = userId.trim()
        if (id.isEmpty()) return ""
        cache[id]?.let { return it }
        val avatar = withContext(Dispatchers.IO) {
            try {
                val raw = postService.getUserInfo(id)
                if (!ApiResponseHelper.isSuccess(raw)) return@withContext ""
                JSONObject(raw).getJSONObject("data").optString("avatar")
            } catch (_: Exception) {
                ""
            }
        }
        val normalized = UserInfoText.normalize(avatar)
        if (normalized.isNotEmpty()) {
            cache[id] = normalized
        }
        return normalized
    }

    suspend fun enrichDanmuItems(items: List<CreatorDanmuItem>): List<CreatorDanmuItem> {
        if (items.isEmpty()) return items
        val needResolve = items.any { UserInfoText.normalize(it.avatar).isEmpty() && it.userId.isNotBlank() }
        if (!needResolve) return items

        items.map { it.userId }.filter { it.isNotBlank() }.distinct().forEach { resolveAvatar(it) }

        return items.map { item ->
            val fromApi = UserInfoText.normalize(item.avatar)
            if (fromApi.isNotEmpty()) return@map item
            val cached = cache[item.userId].orEmpty()
            if (cached.isEmpty()) item else item.copy(avatar = cached)
        }
    }
}
