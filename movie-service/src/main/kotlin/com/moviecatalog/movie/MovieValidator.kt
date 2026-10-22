package com.moviecatalog.movie

import com.moviecatalog.support.GrpcErrors

data class ValidMovie(
    val title: String,
    val releaseYear: Int,
    val runtimeMinutes: Int?,
    val synopsis: String,
    val genres: Set<Genre>,
)

/** Raw movie fields as received; [genres] has null for values that are not in the fixed list. */
data class MovieFields(
    val title: String,
    val releaseYear: Int,
    val runtimeMinutes: Int?,
    val synopsis: String,
    val genres: List<Genre?>,
)

/** Movie rules from BRD section 7. */
object MovieValidator {
    const val EARLIEST_YEAR = 1888
    const val MAX_TITLE = 200
    const val MAX_SYNOPSIS = 2000
    const val MAX_GENRES = 5
    const val FUTURE_YEARS = 5

    fun maxYear(currentYear: Int): Int = currentYear + FUTURE_YEARS

    fun validate(input: MovieFields, currentYear: Int): ValidMovie {
        val violations = mutableListOf<Pair<String, String>>()
        val title = input.title.trim()
        if (title.isEmpty() || title.length > MAX_TITLE) violations += "title" to "movie.title.invalid"
        if (input.releaseYear !in EARLIEST_YEAR..maxYear(currentYear)) violations += "releaseYear" to "movie.year.invalid"
        if (input.runtimeMinutes != null && input.runtimeMinutes !in 1..1000) violations += "runtimeMinutes" to "movie.runtime.invalid"
        if (input.synopsis.length > MAX_SYNOPSIS) violations += "synopsis" to "movie.synopsis.too_long"
        val genres = input.genres
        if (genres.size > MAX_GENRES || genres.any { it == null } || genres.filterNotNull().toSet().size != genres.size) {
            violations += "genres" to "movie.genres.invalid"
        }
        if (violations.isNotEmpty()) throw GrpcErrors.invalidArgument(*violations.toTypedArray())
        return ValidMovie(title, input.releaseYear, input.runtimeMinutes, input.synopsis, genres.filterNotNull().toSet())
    }

    /**
     * Genre filter list (S-702 AC9, ADR-015): at most one of each genre, all known. [genres] has null for values
     * that are not in the fixed list. An empty list means no genre filter.
     */
    fun validateGenreFilter(genres: List<Genre?>): Set<Genre> {
        val known = genres.filterNotNull()
        if (genres.size > Genre.entries.size || known.size != genres.size || known.toSet().size != known.size) {
            throw GrpcErrors.invalidArgument("genres" to "movie.genres.invalid")
        }
        return known.toSet()
    }

    /** Year filter: both bounds in range and from <= to (BRD "Year filter"). */
    fun validateYearRange(from: Int?, to: Int?, currentYear: Int) {
        val range = EARLIEST_YEAR..maxYear(currentYear)
        val bad = (from != null && from !in range) || (to != null && to !in range) || (from != null && to != null && from > to)
        if (bad) throw GrpcErrors.invalidArgument("year" to "filter.year.invalid")
    }
}

/** A credit's fields after validation. */
data class ValidCredit(val role: CreditRole, val characterName: String?)

object CreditValidator {
    const val MAX_CHARACTER = 200

    /** [roleOrNull] is null for an unknown or unspecified role. A blank character name means "none". */
    fun validate(roleOrNull: CreditRole?, characterName: String?): ValidCredit {
        val violations = mutableListOf<Pair<String, String>>()
        if (roleOrNull == null) violations += "role" to "credit.role.invalid"
        val character = characterName?.trim()?.takeIf { it.isNotEmpty() }
        if (character != null && (roleOrNull != CreditRole.ACTOR || character.length > MAX_CHARACTER)) {
            violations += "characterName" to "credit.character.invalid"
        }
        if (violations.isNotEmpty()) throw GrpcErrors.invalidArgument(*violations.toTypedArray())
        return ValidCredit(roleOrNull!!, character)
    }
}
