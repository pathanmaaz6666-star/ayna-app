package com.mazo.ayna.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KnowledgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<KnowledgeEntity>)

    @Query("SELECT * FROM knowledge")
    suspend fun getAll(): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge WHERE category = :category")
    suspend fun searchByCategory(category: String): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KnowledgeEntity?

    @Query("SELECT COUNT(*) FROM knowledge")
    suspend fun count(): Int

    @Query("DELETE FROM knowledge WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
