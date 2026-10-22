package com.moviecatalog.people

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Owned by people-service. `nameKey` is the trimmed, lower-cased name used for sorting and search. */
@Entity
@Table(name = "person")
class Person(
    @Id var id: UUID,
    var name: String,
    var nameKey: String,
    var birthDate: LocalDate?,
    var bio: String,
    var createdAt: Instant,
    var updatedAt: Instant,
)

fun nameKeyOf(name: String): String = name.trim().lowercase(java.util.Locale.ROOT)
