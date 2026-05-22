package com.example.bilibili.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_info")
data class CategoryEntity(
    @PrimaryKey val categoryId: Int,
    val categoryName: String,
    val sortOrder: Int = 0,
)
