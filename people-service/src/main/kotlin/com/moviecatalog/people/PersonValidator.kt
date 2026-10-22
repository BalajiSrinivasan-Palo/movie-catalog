package com.moviecatalog.people

import com.moviecatalog.support.GrpcErrors
import java.time.LocalDate

/** A person's editable fields after validation: name is trimmed. */
data class ValidPerson(val name: String, val birthDate: LocalDate?, val bio: String)

/** Field rules from BRD section 7. Every violation is reported at once, not just the first. */
object PersonValidator {
    val EARLIEST_BIRTH_DATE: LocalDate = LocalDate.of(1850, 1, 1)
    const val MAX_NAME = 200
    const val MAX_BIO = 2000

    /** [birthDate] is null when absent; [birthDateMalformed] is true when a date was sent that is not a real date. */
    fun validate(
        name: String,
        birthDate: LocalDate?,
        birthDateMalformed: Boolean,
        bio: String,
        today: LocalDate,
    ): ValidPerson {
        val violations = mutableListOf<Pair<String, String>>()
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME) violations += "name" to "person.name.invalid"
        if (birthDateMalformed || (birthDate != null && (birthDate.isBefore(EARLIEST_BIRTH_DATE) || birthDate.isAfter(today)))) {
            violations += "birthDate" to "person.birthdate.invalid"
        }
        if (bio.length > MAX_BIO) violations += "bio" to "person.bio.too_long"
        if (violations.isNotEmpty()) throw GrpcErrors.invalidArgument(*violations.toTypedArray())
        return ValidPerson(trimmed, birthDate, bio)
    }
}
