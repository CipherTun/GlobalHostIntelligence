package io.ciphertun.ghi.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val note: String?,
    val createdAt: Long,
)
