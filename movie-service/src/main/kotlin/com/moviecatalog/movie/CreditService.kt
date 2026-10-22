package com.moviecatalog.movie

import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.PageSpec
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class MovieCredits(val credits: List<Credit>, val total: Long) {
    val truncated: Boolean get() = total > credits.size
}

data class FilmographyRow(val credit: Credit, val movie: Movie)

data class PersonCredits(val count: Long, val blockingMovies: List<Movie>)

@Service
class CreditService(
    private val credits: CreditRepository,
    private val movies: MovieRepository,
    private val clock: Clock,
) {
    /** The gateway checks that the person exists before calling this (CR-02); drift is tolerated by BR-07. */
    @Transactional
    fun add(movieId: UUID, personId: UUID, role: CreditRole?, characterName: String?): Credit {
        val valid = CreditValidator.validate(role, characterName)
        if (!movies.existsById(movieId)) throw GrpcErrors.notFound("movie.not_found")
        if (credits.existsByMovieIdAndPersonIdAndRole(movieId, personId, valid.role)) throw GrpcErrors.alreadyExists("credit.duplicate")
        val credit = Credit(UUID.randomUUID(), movieId, personId, valid.role, valid.characterName, Instant.now(clock))
        return saveOrConflict(credit)
    }

    @Transactional
    fun update(creditId: UUID, role: CreditRole?, characterName: String?): Credit {
        val valid = CreditValidator.validate(role, characterName)
        val credit = credits.findById(creditId).orElseThrow { GrpcErrors.notFound("credit.not_found") }
        if (credits.existsByMovieIdAndPersonIdAndRoleAndIdNot(credit.movieId, credit.personId, valid.role, creditId)) {
            throw GrpcErrors.alreadyExists("credit.duplicate")
        }
        credit.role = valid.role
        credit.characterName = valid.characterName
        return saveOrConflict(credit)
    }

    @Transactional
    fun remove(creditId: UUID) {
        credits.delete(credits.findById(creditId).orElseThrow { GrpcErrors.notFound("credit.not_found") })
    }

    /** Up to [limit] credits (1..200) plus the true total, so the UI can say "showing 200 of 350" (EC-14). */
    @Transactional(readOnly = true)
    fun forMovie(movieId: UUID, limit: Int): MovieCredits {
        if (limit !in 0..MAX_PER_MOVIE) throw GrpcErrors.invalidArgument("limit" to "paging.invalid")
        if (!movies.existsById(movieId)) throw GrpcErrors.notFound("movie.not_found")
        val effective = if (limit == 0) MAX_PER_MOVIE else limit
        return MovieCredits(credits.findByMovie(movieId, PageRequest.of(0, effective)), credits.countByMovieId(movieId))
    }

    @Transactional(readOnly = true)
    fun filmography(personId: UUID, page: PageSpec): Slice<FilmographyRow> {
        val result = credits.findFilmography(personId, PageRequest.of(page.index, page.size))
        return Slice(result.content.map { FilmographyRow(it[0] as Credit, it[1] as Movie) }, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun checkPerson(personId: UUID, maxMovies: Int): PersonCredits {
        if (maxMovies !in 0..MAX_BLOCKING) throw GrpcErrors.invalidArgument("maxMovies" to "paging.invalid")
        val limit = if (maxMovies == 0) MAX_BLOCKING else maxMovies
        val count = credits.countByPersonId(personId)
        val blocking = if (count == 0L) emptyList() else movies.findCreditedMovies(personId, PageRequest.of(0, limit))
        return PersonCredits(count, blocking)
    }

    private fun saveOrConflict(credit: Credit): Credit = try {
        credits.saveAndFlush(credit)
    } catch (e: DataIntegrityViolationException) {
        throw GrpcErrors.alreadyExists("credit.duplicate")
    }

    companion object {
        const val MAX_PER_MOVIE = 200
        const val MAX_BLOCKING = 10
    }
}
