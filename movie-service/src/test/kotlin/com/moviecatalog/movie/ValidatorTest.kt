package com.moviecatalog.movie

import com.moviecatalog.support.GrpcErrors
import io.grpc.StatusRuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** BRD section 7: each movie and credit rule has a passing and a failing case (NFR-TEST-01). */
class ValidatorTest {
    private val year = 2026

    private fun fields(
        title: String = "Heat",
        releaseYear: Int = 1995,
        runtime: Int? = null,
        synopsis: String = "",
        genres: List<Genre?> = emptyList(),
    ) = MovieFields(title, releaseYear, runtime, synopsis, genres)

    private fun keys(block: () -> Unit): List<Pair<String, String>> {
        val e = assertFailsWith<StatusRuntimeException> { block() }
        return GrpcErrors.detailOf(e)!!.violationsList.map { it.field to it.messageKey }
    }

    private fun movieKeys(f: MovieFields) = keys { MovieValidator.validate(f, year) }

    @Test
    fun `title is trimmed and 1 to 200 characters`() {
        assertEquals("Heat", MovieValidator.validate(fields(title = "  Heat "), year).title)
        MovieValidator.validate(fields(title = "t".repeat(200)), year)
        assertEquals(listOf("title" to "movie.title.invalid"), movieKeys(fields(title = " ")))
        assertEquals(listOf("title" to "movie.title.invalid"), movieKeys(fields(title = "t".repeat(201))))
    }

    @Test
    fun `release year runs from 1888 to current year plus five`() {
        MovieValidator.validate(fields(releaseYear = 1888), year)
        MovieValidator.validate(fields(releaseYear = 2031), year)
        assertEquals(listOf("releaseYear" to "movie.year.invalid"), movieKeys(fields(releaseYear = 1887)))
        assertEquals(listOf("releaseYear" to "movie.year.invalid"), movieKeys(fields(releaseYear = 2032)))
    }

    @Test
    fun `runtime is optional and 1 to 1000`() {
        MovieValidator.validate(fields(runtime = null), year)
        MovieValidator.validate(fields(runtime = 1), year)
        MovieValidator.validate(fields(runtime = 1000), year)
        assertEquals(listOf("runtimeMinutes" to "movie.runtime.invalid"), movieKeys(fields(runtime = 0)))
        assertEquals(listOf("runtimeMinutes" to "movie.runtime.invalid"), movieKeys(fields(runtime = 1001)))
    }

    @Test
    fun `synopsis limit is 2000 characters`() {
        MovieValidator.validate(fields(synopsis = "s".repeat(2000)), year)
        assertEquals(listOf("synopsis" to "movie.synopsis.too_long"), movieKeys(fields(synopsis = "s".repeat(2001))))
    }

    @Test
    fun `genres allow up to five known values without duplicates`() {
        MovieValidator.validate(fields(genres = listOf(Genre.ACTION, Genre.SCI_FI, Genre.WAR, Genre.DRAMA, Genre.CRIME)), year)
        val bad = "genres" to "movie.genres.invalid"
        assertEquals(listOf(bad), movieKeys(fields(genres = Genre.entries.take(6))))
        assertEquals(listOf(bad), movieKeys(fields(genres = listOf(Genre.ACTION, Genre.ACTION))))
        assertEquals(listOf(bad), movieKeys(fields(genres = listOf(Genre.ACTION, null))))
    }

    @Test
    fun `S-702 AC9 genre filter allows up to all 15 known genres without duplicates`() {
        assertEquals(emptySet(), MovieValidator.validateGenreFilter(emptyList()))
        assertEquals(setOf(Genre.COMEDY, Genre.CRIME), MovieValidator.validateGenreFilter(listOf(Genre.COMEDY, Genre.CRIME)))
        assertEquals(Genre.entries.toSet(), MovieValidator.validateGenreFilter(Genre.entries))
        val bad = listOf("genres" to "movie.genres.invalid")
        assertEquals(bad, keys { MovieValidator.validateGenreFilter(listOf(Genre.COMEDY, Genre.COMEDY)) })
        assertEquals(bad, keys { MovieValidator.validateGenreFilter(listOf(Genre.COMEDY, null)) })
        assertEquals(bad, keys { MovieValidator.validateGenreFilter(Genre.entries + Genre.ACTION) }) // 16 entries
    }

    @Test
    fun `year filter needs both bounds in range and from not after to`() {
        MovieValidator.validateYearRange(null, null, year)
        MovieValidator.validateYearRange(2000, 2000, year)
        val bad = listOf("year" to "filter.year.invalid")
        assertEquals(bad, keys { MovieValidator.validateYearRange(2001, 2000, year) })
        assertEquals(bad, keys { MovieValidator.validateYearRange(1800, null, year) })
        assertEquals(bad, keys { MovieValidator.validateYearRange(null, 3000, year) })
    }

    @Test
    fun `character names are only for actors and at most 200 characters`() {
        assertEquals("Neo", CreditValidator.validate(CreditRole.ACTOR, " Neo ").characterName)
        assertEquals(null, CreditValidator.validate(CreditRole.ACTOR, "  ").characterName)
        assertEquals(null, CreditValidator.validate(CreditRole.DIRECTOR, null).characterName)
        CreditValidator.validate(CreditRole.ACTOR, "c".repeat(200))
        val bad = listOf("characterName" to "credit.character.invalid")
        assertEquals(bad, keys { CreditValidator.validate(CreditRole.DIRECTOR, "Neo") })
        assertEquals(bad, keys { CreditValidator.validate(CreditRole.ACTOR, "c".repeat(201)) })
    }

    @Test
    fun `unknown role is rejected`() {
        assertEquals(listOf("role" to "credit.role.invalid"), keys { CreditValidator.validate(null, null) })
    }
}
