package com.moviecatalog.movie

import com.moviecatalog.support.PageSpec
import com.moviecatalog.support.escapeLike
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Locale
import java.util.UUID

data class Slice<T>(val items: List<T>, val total: Long)

enum class MovieSort { TITLE, RELEASE_YEAR }

interface MovieRepository : JpaRepository<Movie, UUID> {
    fun existsByTitleKeyAndReleaseYear(titleKey: String, releaseYear: Int): Boolean
    fun existsByTitleKeyAndReleaseYearAndIdNot(titleKey: String, releaseYear: Int, id: UUID): Boolean

    /** At most [Pageable.getPageSize] distinct movies that credit the person, for the delete-blocking message. */
    @Query("select m from Movie m where m.id in (select c.movieId from Credit c where c.personId = :personId) order by m.titleKey, m.id")
    fun findCreditedMovies(@Param("personId") personId: UUID, pageable: Pageable): List<Movie>
}

interface CreditRepository : JpaRepository<Credit, UUID> {
    fun countByMovieId(movieId: UUID): Long
    fun countByPersonId(personId: UUID): Long
    fun existsByMovieIdAndPersonIdAndRole(movieId: UUID, personId: UUID, role: CreditRole): Boolean
    fun existsByMovieIdAndPersonIdAndRoleAndIdNot(movieId: UUID, personId: UUID, role: CreditRole, id: UUID): Boolean

    /** ACTOR, DIRECTOR, WRITER, PRODUCER (S-402.AC5), not alphabetical, so the order is spelled out. */
    @Query(
        "select c from Credit c where c.movieId = :movieId order by " +
            "case c.role when com.moviecatalog.movie.CreditRole.ACTOR then 0 " +
            "when com.moviecatalog.movie.CreditRole.DIRECTOR then 1 " +
            "when com.moviecatalog.movie.CreditRole.WRITER then 2 else 3 end, c.createdAt, c.id",
    )
    fun findByMovie(@Param("movieId") movieId: UUID, pageable: Pageable): List<Credit>

    /** Filmography: newest movie first. Each row is [Credit, Movie]. */
    @Query(
        value = "select c, m from Credit c, Movie m where c.movieId = m.id and c.personId = :personId " +
            "order by m.releaseYear desc, m.titleKey, c.role, c.id",
        countQuery = "select count(c) from Credit c where c.personId = :personId",
    )
    fun findFilmography(@Param("personId") personId: UUID, pageable: Pageable): org.springframework.data.domain.Page<Array<Any>>

    /** Rows of [movieId, count] for a set of movies, so a list page costs one query instead of one per movie. */
    @Query("select c.movieId, count(c) from Credit c where c.movieId in :ids group by c.movieId")
    fun countsByMovie(@Param("ids") ids: Collection<UUID>): List<Array<Any>>
}

/** Movie list queries use the Criteria API: every value is a bound parameter (NFR-SEC-04). */
@Repository
class MovieQueries(private val em: EntityManager) {
    /** [genres]: a movie matches when it has any of them; empty means no genre filter. */
    fun list(genres: Set<Genre>, yearFrom: Int?, yearTo: Int?, sort: MovieSort, descending: Boolean, page: PageSpec): Slice<Movie> {
        val cb = em.criteriaBuilder

        val countQuery = cb.createQuery(Long::class.javaObjectType)
        val countRoot = countQuery.from(Movie::class.java)
        countQuery.select(cb.count(countRoot)).where(*predicates(cb, countRoot, genres, yearFrom, yearTo).toTypedArray())
        val total = em.createQuery(countQuery).singleResult

        val query = cb.createQuery(Movie::class.java)
        val root = query.from(Movie::class.java)
        query.select(root).where(*predicates(cb, root, genres, yearFrom, yearTo).toTypedArray())
        val column = if (sort == MovieSort.TITLE) root.get<String>("titleKey") else root.get<Int>("releaseYear")
        // id as the last key keeps paging stable when titles or years are equal
        query.orderBy(if (descending) cb.desc(column) else cb.asc(column), cb.asc(root.get<UUID>("id")))
        val items = em.createQuery(query).setFirstResult(page.offset).setMaxResults(page.size).resultList
        return Slice(items, total)
    }

    /**
     * Substring match on title, synopsis and genre names, ignoring case (FR-SRC-01, D9). `%`, `_` and `\` in
     * [query] are literal. Genre names are matched in code against the fixed list, so "sci-fi" finds Sci-Fi
     * movies while "sci_fi" matches nothing. Results are ordered by title.
     */
    fun search(query: String, page: PageSpec): Slice<Movie> {
        val cb = em.criteriaBuilder
        val needle = query.lowercase(Locale.ROOT)
        val pattern = "%" + escapeLike(needle) + "%"
        val genres = Genre.entries.filter { it.display.lowercase(Locale.ROOT).contains(needle) }
        fun matches(root: jakarta.persistence.criteria.Root<Movie>): Predicate {
            val any = mutableListOf<Predicate>(
                cb.like(root.get("titleKey"), pattern, '\\'),
                cb.like(cb.lower(root.get<String>("synopsis")), pattern, '\\'),
            )
            genres.forEach { any += cb.isMember(it, root.get<Set<Genre>>("genres")) }
            return cb.or(*any.toTypedArray())
        }

        val countQuery = cb.createQuery(Long::class.javaObjectType)
        val countRoot = countQuery.from(Movie::class.java)
        countQuery.select(cb.count(countRoot)).where(matches(countRoot))
        val total = em.createQuery(countQuery).singleResult

        val q = cb.createQuery(Movie::class.java)
        val root = q.from(Movie::class.java)
        q.select(root).where(matches(root)).orderBy(cb.asc(root.get<String>("titleKey")), cb.asc(root.get<UUID>("id")))
        val items = em.createQuery(q).setFirstResult(page.offset).setMaxResults(page.size).resultList
        return Slice(items, total)
    }

    private fun predicates(
        cb: jakarta.persistence.criteria.CriteriaBuilder,
        root: jakarta.persistence.criteria.Root<Movie>,
        genres: Set<Genre>,
        yearFrom: Int?,
        yearTo: Int?,
    ): List<Predicate> = buildList {
        // isMember is a subquery, not a join, so a movie with several matching genres is still one row.
        if (genres.isNotEmpty()) add(cb.or(*genres.map { cb.isMember(it, root.get<Set<Genre>>("genres")) }.toTypedArray()))
        if (yearFrom != null) add(cb.greaterThanOrEqualTo(root.get("releaseYear"), yearFrom))
        if (yearTo != null) add(cb.lessThanOrEqualTo(root.get("releaseYear"), yearTo))
    }
}
