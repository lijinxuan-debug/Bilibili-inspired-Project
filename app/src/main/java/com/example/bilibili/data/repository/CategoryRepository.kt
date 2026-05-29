package com.example.bilibili.data.repository

import com.example.bilibili.data.api.CategoryInfoService
import com.example.bilibili.data.local.AppDatabase
import com.example.bilibili.data.local.CategoryEntity
import com.example.bilibili.data.model.CategoryItem
import org.json.JSONObject

class CategoryRepository(
    private val database: AppDatabase,
    private val categoryService: CategoryInfoService,
) {

    private val categoryDao = database.categoryDao()

    /**
     * 优先读本地 Room；仅在没有缓存时请求网络并写入数据库。
     */
    suspend fun loadCategories(): List<CategoryItem> {
        val cached = categoryDao.getAll()
        if (cached.isNotEmpty()) {
            return toDisplayList(cached)
        }
        return fetchAndCache()
    }

    private suspend fun fetchAndCache(): List<CategoryItem> {
        val response = categoryService.loadAllCategoryInfo()
        val parsed = parseCategoryJson(response)
        categoryDao.clearAll()
        categoryDao.insertAll(
            parsed.mapIndexed { index, item ->
                CategoryEntity(
                    categoryId = item.categoryId,
                    categoryName = item.categoryName,
                    sortOrder = index,
                )
            }
        )
        return toDisplayList(categoryDao.getAll())
    }

    private fun toDisplayList(entities: List<CategoryEntity>): List<CategoryItem> {
        val items = entities.map { CategoryItem(it.categoryId, it.categoryName) }
        return listOf(CategoryItem(-1, "全部")) + items
    }

    private fun parseCategoryJson(json: String): List<CategoryItem> {
        val list = mutableListOf<CategoryItem>()
        val root = JSONObject(json)
        if (root.optInt("code") != 200) return list
        val dataArray = root.optJSONArray("data") ?: return list
        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            list.add(
                CategoryItem(
                    categoryId = item.getInt("categoryId"),
                    categoryName = item.getString("categoryName"),
                ),
            )
            val children = item.optJSONArray("children") ?: continue
            for (j in 0 until children.length()) {
                val child = children.getJSONObject(j)
                list.add(
                    CategoryItem(
                        categoryId = child.getInt("categoryId"),
                        categoryName = child.getString("categoryName"),
                    ),
                )
            }
        }
        return list
    }
}
