package com.example.bilibili.data.repository

import android.content.Context
import com.example.bilibili.data.api.CategoryInfoService
import com.example.bilibili.data.local.AppDatabase
import com.example.bilibili.data.model.CategoryInfo
import com.example.bilibili.util.ApiResponseHelper
import com.example.bilibili.util.CategoryPartitionHelper
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * 加载全部分区（含二级）并缓存，供编辑页回填分区名称。
 */
object CategoryTreeLoader {

    private data class FlatCategory(
        val id: Int,
        val name: String,
        val parentId: Int?,
    )

    @Volatile
    private var flatCache: Map<Int, FlatCategory>? = null

    suspend fun resolvePartition(
        context: Context,
        pCategoryId: Int,
        categoryId: Int,
    ): CategoryInfo? {
        if (pCategoryId <= 0 && categoryId <= 0) return null
        val map = ensureFlatMap(context)
        resolveFromFlat(pCategoryId, categoryId, map)?.let { info ->
            if (CategoryPartitionHelper.displayName(info).isNotBlank()) return info
        }
        return CategoryPartitionHelper.resolveFromIds(pCategoryId, categoryId, toJsonArray(map))
            ?.takeIf { CategoryPartitionHelper.displayName(it).isNotBlank() }
    }

    private suspend fun ensureFlatMap(context: Context): Map<Int, FlatCategory> {
        flatCache?.let { return it }
        val map = linkedMapOf<Int, FlatCategory>()
        val service = com.example.bilibili.util.RetrofitClient
            .create(CategoryInfoService::class.java)
        repeat(2) { attempt ->
            val response = service.loadAllCategoryInfo()
            val data = parseTreeArray(response)
            if (data != null && data.length() > 0) {
                flattenTree(data, map)
                if (map.isNotEmpty()) {
                    flatCache = map
                    return map
                }
            }
            if (attempt == 0) delay(120)
        }
        val roomParents = AppDatabase.getInstance(context).categoryDao().getAll()
        if (roomParents.isEmpty()) {
            runCatching {
                CategoryRepository(
                    AppDatabase.getInstance(context),
                    service,
                ).loadCategories()
            }
            AppDatabase.getInstance(context).categoryDao().getAll().forEach { entity ->
                map[entity.categoryId] = FlatCategory(entity.categoryId, entity.categoryName, null)
            }
        } else {
            roomParents.forEach { entity ->
                map[entity.categoryId] = FlatCategory(entity.categoryId, entity.categoryName, null)
            }
        }
        flatCache = map
        return map
    }

    private fun parseTreeArray(response: String): JSONArray? {
        CategoryPartitionHelper.parseCategoryTreeData(response)?.let { return it }
        return try {
            val root = JSONObject(response)
            if (!ApiResponseHelper.isSuccess(response)) return null
            root.optJSONArray("data")?.takeIf { it.length() > 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun flattenTree(data: JSONArray, map: MutableMap<Int, FlatCategory>) {
        for (i in 0 until data.length()) {
            val parent = data.getJSONObject(i)
            val parentId = parent.optInt("categoryId")
            if (parentId <= 0) continue
            map[parentId] = FlatCategory(
                id = parentId,
                name = parent.optString("categoryName"),
                parentId = null,
            )
            val children = parent.optJSONArray("children") ?: continue
            for (j in 0 until children.length()) {
                val child = children.getJSONObject(j)
                val childId = child.optInt("categoryId")
                if (childId <= 0) continue
                map[childId] = FlatCategory(
                    id = childId,
                    name = child.optString("categoryName"),
                    parentId = parentId,
                )
            }
        }
    }

    private fun resolveFromFlat(
        pCategoryId: Int,
        categoryId: Int,
        map: Map<Int, FlatCategory>,
    ): CategoryInfo? {
        if (map.isEmpty()) return null
        if (pCategoryId > 0) {
            val parent = map[pCategoryId]
            val parentName = parent?.name.orEmpty()
            if (categoryId > 0 && categoryId != pCategoryId) {
                val sub = map[categoryId]
                return CategoryInfo(
                    categoryId = pCategoryId,
                    categoryName = parentName,
                    subCategoryId = categoryId,
                    subCategoryName = sub?.name.orEmpty(),
                )
            }
            if (parentName.isNotEmpty() || parent != null) {
                return CategoryInfo(categoryId = pCategoryId, categoryName = parentName)
            }
        }
        if (categoryId > 0) {
            val sub = map[categoryId] ?: return null
            val parentId = sub.parentId ?: categoryId
            val parent = map[parentId]
            return CategoryInfo(
                categoryId = parentId,
                categoryName = parent?.name.orEmpty(),
                subCategoryId = categoryId.takeIf { it != parentId },
                subCategoryName = sub.name.takeIf { categoryId != parentId },
            )
        }
        return null
    }

    private fun toJsonArray(map: Map<Int, FlatCategory>): JSONArray {
        val parents = map.values.filter { it.parentId == null }
        val arr = JSONArray()
        parents.forEach { parent ->
            val obj = JSONObject()
                .put("categoryId", parent.id)
                .put("categoryName", parent.name)
            val children = JSONArray()
            map.values.filter { it.parentId == parent.id }.forEach { child ->
                children.put(
                    JSONObject()
                        .put("categoryId", child.id)
                        .put("categoryName", child.name),
                )
            }
            if (children.length() > 0) {
                obj.put("children", children)
            }
            arr.put(obj)
        }
        return arr
    }

    fun clearCache() {
        flatCache = null
    }
}
