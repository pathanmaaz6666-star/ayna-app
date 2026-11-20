package com.mazo.ayna.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the offline knowledge bank.
 * keywordsJson holds a JSON array of lowercase match words
 * (Roman Urdu + English + Urdu script where natural), e.g.
 * ["namaz", "tarika", "نماز"].
 */
@Entity(tableName = "knowledge")
data class KnowledgeEntity(
    @PrimaryKey val id: String,
    val category: String,
    val language: String,
    val keywordsJson: String,
    val question: String,
    val answer: String,
    val version: Int
)
