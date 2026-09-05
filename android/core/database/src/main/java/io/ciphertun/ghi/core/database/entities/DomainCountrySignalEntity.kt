package io.ciphertun.ghi.core.database.entities

import androidx.room.Entity

@Entity(tableName = "domain_country_signals", primaryKeys = ["domainId", "signalType"])
data class DomainCountrySignalEntity(
    val domainId: String,
    val signalType: String,
    val countryCode: String,
    val confidence: Double,
    val evidence: String?,
)
