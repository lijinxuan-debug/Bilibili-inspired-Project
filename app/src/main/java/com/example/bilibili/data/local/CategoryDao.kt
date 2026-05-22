package com.example.bilibili.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category_info ORDER BY sortOrder ASC, categoryId ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM category_info")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM category_info")
    suspend fun count(): Int
}
