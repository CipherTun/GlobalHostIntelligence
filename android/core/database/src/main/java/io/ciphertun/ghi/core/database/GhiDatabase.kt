package io.ciphertun.ghi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.ciphertun.ghi.core.database.dao.BookmarkDao
import io.ciphertun.ghi.core.database.dao.DomainDao
import io.ciphertun.ghi.core.database.entities.BookmarkEntity
import io.ciphertun.ghi.core.database.entities.DomainCountrySignalEntity
import io.ciphertun.ghi.core.database.entities.DomainEntity

@Database(
    entities = [DomainEntity::class, DomainCountrySignalEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GhiDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun bookmarkDao(): BookmarkDao
}
