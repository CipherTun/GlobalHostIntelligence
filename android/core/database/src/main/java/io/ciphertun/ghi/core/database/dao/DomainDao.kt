package io.ciphertun.ghi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.ciphertun.ghi.core.database.entities.DomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Upsert
    suspend fun upsertAll(domains: List<DomainEntity>)

    @Query("SELECT * FROM domains WHERE id = :id")
    suspend fun byId(id: String): DomainEntity?

    @Query("SELECT * FROM domains ORDER BY lastSeenAt DESC LIMIT :limit")
    fun recent(limit: Int = 100): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE fqdn LIKE '%' || :query || '%' LIMIT 50")
    suspend fun search(query: String): List<DomainEntity>
}
