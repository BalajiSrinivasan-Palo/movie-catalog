package com.moviecatalog.movie

import com.moviecatalog.movie.artwork.Artwork
import com.moviecatalog.movie.artwork.ArtworkService
import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.PageSpec
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** A movie with its credit count and artwork (if any), which the API returns on every movie. */
data class MovieView(val movie: Movie, val creditCount: Long, val artwork: Artwork? = null)

@Service
class MovieService(
    private val movies: MovieRepository,
    private val credits: CreditRepository,
    private val queries: MovieQueries,
    private val artworks: ArtworkService,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)
    private fun currentYear(): Int = java.time.LocalDate.now(clock.withZone(ZoneOffset.UTC)).year

    @Transactional
    fun create(fields: MovieFields): MovieView {
        val valid = MovieValidator.validate(fields, currentYear())
        val key = titleKeyOf(valid.title)
        if (movies.existsByTitleKeyAndReleaseYear(key, valid.releaseYear)) throw GrpcErrors.alreadyExists("movie.duplicate")
        val now = Instant.now(clock)
        val movie = Movie(UUID.randomUUID(), valid.title, key, valid.releaseYear, valid.runtimeMinutes, valid.synopsis, valid.genres.toMutableSet(), now, now)
        return MovieView(saveOrConflict(movie), 0)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): MovieView = MovieView(find(id), credits.countByMovieId(id), artworks.find(id))

    /** Full replacement of every editable field (last write wins, BR-12). */
    @Transactional
    fun update(id: UUID, fields: MovieFields): MovieView {
        val valid = MovieValidator.validate(fields, currentYear())
        val movie = find(id)
        val key = titleKeyOf(valid.title)
        if (movies.existsByTitleKeyAndReleaseYearAndIdNot(key, valid.releaseYear, id)) throw GrpcErrors.alreadyExists("movie.duplicate")
        movie.title = valid.title
        movie.titleKey = key
        movie.releaseYear = valid.releaseYear
        movie.runtimeMinutes = valid.runtimeMinutes
        movie.synopsis = valid.synopsis
        movie.genres.clear()
        movie.genres.addAll(valid.genres)
        movie.updatedAt = Instant.now(clock)
        return MovieView(saveOrConflict(movie), credits.countByMovieId(id), artworks.find(id))
    }

    /** Credits go with the movie through the foreign key's ON DELETE CASCADE (BR-03). */
    fun delete(id: UUID) {
        // The lock is taken outside the transaction, so a concurrent upload for this movie cannot commit between
        // reading its artwork row and the delete committing (which would leave files without a row).
        artworks.underMovieLock(id) {
            tx.executeWithoutResult {
                val movie = find(id)
                artworks.deleteFilesAfterCommit(id) // reads the row now, deletes the files only if the transaction commits
                movies.delete(movie)
                movies.flush()
            }
        }
    }

    /**
     * [genre] is the older single-genre filter; [genres] is the list from S-702. They are combined, and a movie
     * matches when it has any of them (ADR-015).
     */
    @Transactional(readOnly = true)
    fun list(
        genre: Genre?,
        yearFrom: Int?,
        yearTo: Int?,
        sort: MovieSort,
        descending: Boolean,
        page: PageSpec,
        genres: List<Genre?> = emptyList(),
    ): Slice<MovieView> {
        val anyOf = MovieValidator.validateGenreFilter(genres) + setOfNotNull(genre)
        MovieValidator.validateYearRange(yearFrom, yearTo, currentYear())
        val slice = queries.list(anyOf, yearFrom, yearTo, sort, descending, page)
        return Slice(withCounts(slice.items), slice.total)
    }

    /** Substring search over title, synopsis and genre names; 2..100 characters after trimming (D9, EC-12). */
    @Transactional(readOnly = true)
    fun search(query: String, page: PageSpec): Slice<MovieView> {
        val q = query.trim()
        if (q.length !in 2..100) throw GrpcErrors.invalidArgument("query" to "search.query.invalid")
        val slice = queries.search(q, page)
        return Slice(withCounts(slice.items), slice.total)
    }

    fun withCounts(items: List<Movie>): List<MovieView> {
        if (items.isEmpty()) return emptyList()
        val ids = items.map { it.id }
        val counts = credits.countsByMovie(ids).associate { (it[0] as UUID) to (it[1] as Long) }
        val art = artworks.forMovies(ids)
        return items.map { MovieView(it, counts[it.id] ?: 0, art[it.id]) }
    }

    fun find(id: UUID): Movie = movies.findById(id).orElseThrow { GrpcErrors.notFound("movie.not_found") }

    /** The unique constraint is the real guard against two concurrent creates of the same movie. */
    private fun saveOrConflict(movie: Movie): Movie = try {
        movies.saveAndFlush(movie)
    } catch (e: DataIntegrityViolationException) {
        throw GrpcErrors.alreadyExists("movie.duplicate")
    }

}
