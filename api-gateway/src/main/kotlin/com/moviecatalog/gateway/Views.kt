package com.moviecatalog.gateway

import com.moviecatalog.contracts.common.v1.PageInfo as PageInfoMessage
import com.moviecatalog.contracts.common.v1.PageRequest
import com.moviecatalog.contracts.movie.v1.Credit as CreditMessage
import com.moviecatalog.contracts.movie.v1.CreditRole as CreditRoleMessage
import com.moviecatalog.contracts.movie.v1.Genre as GenreMessage
import com.moviecatalog.contracts.movie.v1.Movie as MovieMessage
import com.moviecatalog.contracts.movie.v1.MovieSummary as MovieSummaryMessage
import com.moviecatalog.contracts.people.v1.Person as PersonMessage
import com.moviecatalog.support.toLocalDateOrNull
import java.time.Instant
import java.time.LocalDate

// ---- GraphQL enums (names match schema.graphqls; the proto names carry a prefix) ----

enum class Genre { ACTION, ADVENTURE, ANIMATION, COMEDY, CRIME, DOCUMENTARY, DRAMA, FANTASY, HORROR, MYSTERY, ROMANCE, SCI_FI, THRILLER, WAR, WESTERN }

enum class CreditRole { ACTOR, DIRECTOR, WRITER, PRODUCER }

enum class SortDirection { ASC, DESC }

enum class MovieSortField { TITLE, RELEASE_YEAR }

enum class PersonSortField { NAME }

fun Genre.toMessage(): GenreMessage = GenreMessage.valueOf("GENRE_$name")
fun GenreMessage.toGraphQl(): Genre = Genre.valueOf(name.removePrefix("GENRE_"))
fun CreditRole.toMessage(): CreditRoleMessage = CreditRoleMessage.valueOf("CREDIT_ROLE_$name")
fun CreditRoleMessage.toGraphQl(): CreditRole = CreditRole.valueOf(name.removePrefix("CREDIT_ROLE_"))

// ---- inputs ----

data class PageInput(val page: Int = 0, val size: Int = 20)
data class MovieInput(
    val title: String,
    val releaseYear: Int,
    val runtimeMinutes: Int? = null,
    val synopsis: String = "",
    val genres: List<Genre> = emptyList(),
)
/** [genre] is deprecated; [genres] matches any listed genre (S-702, ADR-015). movie-service validates both. */
data class MovieFilter(
    val genre: Genre? = null,
    val genres: List<Genre>? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
)
data class MovieSort(val field: MovieSortField = MovieSortField.TITLE, val direction: SortDirection = SortDirection.ASC)
data class PersonInput(val name: String, val birthDate: LocalDate? = null, val bio: String = "")
data class PersonSort(val field: PersonSortField = PersonSortField.NAME, val direction: SortDirection = SortDirection.ASC)
data class CreditInput(val personId: String, val role: CreditRole, val characterName: String? = null)
data class CreditUpdateInput(val role: CreditRole, val characterName: String? = null)

fun PageInput?.toRequest(): PageRequest {
    val p = this ?: PageInput()
    return PageRequest.newBuilder().setPage(p.page).setSize(p.size).build()
}

// ---- output views ----

data class PageInfo(val page: Int, val size: Int, val totalElements: Int, val totalPages: Int)

fun PageInfoMessage.toView() = PageInfo(page, size, totalElements.toInt(), totalPages)

data class PersonView(
    val id: String,
    val name: String,
    val birthDate: LocalDate?,
    val bio: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun PersonMessage.toView() = PersonView(
    id, name, if (hasBirthDate()) birthDate.toLocalDateOrNull() else null, bio,
    Instant.ofEpochSecond(createdAt.seconds, createdAt.nanos.toLong()),
    Instant.ofEpochSecond(updatedAt.seconds, updatedAt.nanos.toLong()),
)

data class PersonPage(val items: List<PersonView>, val pageInfo: PageInfo)

enum class PersonStatus { RESOLVED, NOT_FOUND, UNAVAILABLE }

data class CreditView(
    val id: String,
    val role: CreditRole,
    val characterName: String?,
    val personStatus: PersonStatus,
    val person: PersonView?,
    val personId: String,
    val movieId: String,
)

/** The credits of one movie, loaded once on demand and shared by `credits` and `creditsTruncated`. */
data class CreditBundle(val credits: List<CreditView>, val truncated: Boolean)

data class MovieView(
    val id: String,
    val title: String,
    val releaseYear: Int,
    val runtimeMinutes: Int?,
    val synopsis: String,
    val genres: List<Genre>,
    val artwork: ArtworkView?,
    val creditCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Not a schema field: lets `credits` and `creditsTruncated` share one downstream call. */
    val creditBundle: Lazy<CreditBundle>,
)

data class ArtworkView(
    val thumbnailUrl: String,
    val url: String,
    val width: Int,
    val height: Int,
    val contentType: String,
    val sizeBytes: Int,
)

data class MoviePage(val items: List<MovieView>, val pageInfo: PageInfo)

data class SearchResult(val movies: MoviePage, val people: PersonPage)

data class MovieSummaryView(val id: String, val title: String, val releaseYear: Int, val thumbnailUrl: String?)

fun MovieSummaryMessage.toView() = MovieSummaryView(
    id, title, releaseYear,
    if (hasArtwork) "/artwork/$id/thumbnail?v=$artworkVersion" else null,
)

data class FilmographyEntry(val creditId: String, val role: CreditRole, val characterName: String?, val movie: MovieSummaryView)
data class FilmographyPage(val items: List<FilmographyEntry>, val pageInfo: PageInfo)

fun MovieMessage.toView(credits: Lazy<CreditBundle>) = MovieView(
    id, title, releaseYear, if (hasRuntimeMinutes()) runtimeMinutes else null, synopsis,
    genresList.map { it.toGraphQl() },
    if (hasArtwork()) artwork.let {
        ArtworkView("/artwork/$id/thumbnail?v=${it.version}", "/artwork/$id/original?v=${it.version}", it.width, it.height, it.contentType, it.sizeBytes.toInt())
    } else null,
    creditCount,
    Instant.ofEpochSecond(createdAt.seconds, createdAt.nanos.toLong()),
    Instant.ofEpochSecond(updatedAt.seconds, updatedAt.nanos.toLong()),
    credits,
)

fun CreditMessage.toView(status: PersonStatus, person: PersonView?) = CreditView(
    id, role.toGraphQl(), if (hasCharacterName()) characterName else null, status, person, personId, movieId,
)
