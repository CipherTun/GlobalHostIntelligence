package io.ciphertun.ghi.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import io.ciphertun.ghi.core.database.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert
    suspend fun insert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun all(): Flow<List<BookmarkEntity>>
}
