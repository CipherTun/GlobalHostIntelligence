package io.ciphertun.ghi.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room cache row for a Domain. Deliberately flat (no embedded country
 * signals list — those live in DomainCountrySignalEntity, joined by
 * domainId) since Room doesn't persist nested collections directly.
 */
@Entity(tableName = "domains")
data class DomainEntity(
    @PrimaryKey val id: String,
    val fqdn: String,
    val tld: String,
    val registrableDomain: String,
    val primaryIpId: String?,
    val currentCertificateId: String?,
    val httpStatus: Int?,
    val httpServerHeader: String?,
    val tlsVersion: String?,
    val discoveredVia: String?,
    val discoveredAt: String,
    val lastSeenAt: String,
    val cachedAt: Long,
)
