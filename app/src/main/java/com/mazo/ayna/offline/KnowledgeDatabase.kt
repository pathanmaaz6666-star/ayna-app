package com.mazo.ayna.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [KnowledgeEntity::class], version = 1, exportSchema = false)
abstract class KnowledgeDatabase : RoomDatabase() {

    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        private const val DB_NAME = "ayna_knowledge.db"

        @Volatile
        private var INSTANCE: KnowledgeDatabase? = null

        fun getInstance(context: Context): KnowledgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KnowledgeDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
