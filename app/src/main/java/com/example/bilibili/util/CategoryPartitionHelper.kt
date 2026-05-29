package com.example.bilibili.util

import com.example.bilibili.data.model.CategoryInfo
import org.json.JSONArray
import org.json.JSONObject

object CategoryPartitionHelper {

    fun displayName(info: CategoryInfo?): String {
        if (info == null) return ""
        val parent = info.categoryName.trim()
        val sub = info.subCategoryName?.trim().orEmpty()
        return when {
            parent.isNotEmpty() && sub.isNotEmpty() -> "$parent · $sub"
            sub.isNotEmpty() -> sub
            else -> parent
        }
    }

    /**
     * 根据稿件里的 pCategoryId（一级）和 categoryId（二级，可为 0）解析完整分区信息。
     */
    fun resolveFromIds(pCategoryId: Int, categoryId: Int, categoryTreeResponse: String): CategoryInfo? {
        if (pCategoryId <= 0) return null
        return try {
            val data = JSONObject(categoryTreeResponse).optJSONArray("data") ?: return null
            resolveFromTree(pCategoryId, categoryId, data)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveFromTree(
        pCategoryId: Int,
        categoryId: Int,
        data: JSONArray,
    ): CategoryInfo? {
        for (i in 0 until data.length()) {
            val parent = data.getJSONObject(i)
            if (parent.optInt("categoryId") != pCategoryId) continue
            val parentName = parent.optString("categoryName")
            if (categoryId <= 0 || categoryId == pCategoryId) {
                return CategoryInfo(
                    categoryId = pCategoryId,
                    categoryName = parentName,
                )
            }
            val children = parent.optJSONArray("children") ?: JSONArray()
            for (j in 0 until children.length()) {
                val child = children.getJSONObject(j)
                if (child.optInt("categoryId") == categoryId) {
                    return CategoryInfo(
                        categoryId = pCategoryId,
                        categoryName = parentName,
                        subCategoryId = categoryId,
                        subCategoryName = child.optString("categoryName"),
                    )
                }
            }
            return CategoryInfo(categoryId = pCategoryId, categoryName = parentName)
        }
        return null
    }
}
