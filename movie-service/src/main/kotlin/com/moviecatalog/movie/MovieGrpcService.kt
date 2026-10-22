package com.moviecatalog.movie

import com.moviecatalog.contracts.common.v1.SortDirection
import com.moviecatalog.contracts.movie.v1.AddCreditRequest
import com.moviecatalog.contracts.movie.v1.ArtworkInfo
import com.moviecatalog.contracts.movie.v1.DownloadArtworkRequest
import com.moviecatalog.contracts.movie.v1.DownloadArtworkResponse
import com.moviecatalog.contracts.movie.v1.RemoveArtworkRequest
import com.moviecatalog.contracts.movie.v1.RemoveArtworkResponse
import com.moviecatalog.contracts.movie.v1.UploadArtworkRequest
import com.moviecatalog.contracts.movie.v1.CheckPersonCreditsRequest
import com.moviecatalog.contracts.movie.v1.CheckPersonCreditsResponse
import com.moviecatalog.contracts.movie.v1.CreateMovieRequest
import com.moviecatalog.contracts.movie.v1.CreditWithMovie
import com.moviecatalog.contracts.movie.v1.DeleteMovieRequest
import com.moviecatalog.contracts.movie.v1.DeleteMovieResponse
import com.moviecatalog.contracts.movie.v1.GetMovieRequest
import com.moviecatalog.contracts.movie.v1.ListCreditsByMovieRequest
import com.moviecatalog.contracts.movie.v1.ListCreditsByMovieResponse
import com.moviecatalog.contracts.movie.v1.ListCreditsByPersonRequest
import com.moviecatalog.contracts.movie.v1.ListCreditsByPersonResponse
import com.moviecatalog.contracts.movie.v1.ListMoviesRequest
import com.moviecatalog.contracts.movie.v1.ListMoviesResponse
import com.moviecatalog.contracts.movie.v1.MovieInput
import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.contracts.movie.v1.MovieSortField
import com.moviecatalog.contracts.movie.v1.RemoveCreditRequest
import com.moviecatalog.contracts.movie.v1.SearchMoviesRequest
import com.moviecatalog.contracts.movie.v1.RemoveCreditResponse
import com.moviecatalog.contracts.movie.v1.UpdateCreditRequest
import com.moviecatalog.contracts.movie.v1.UpdateMovieRequest
import com.moviecatalog.movie.artwork.Artwork
import com.moviecatalog.movie.artwork.ArtworkIngest
import com.moviecatalog.movie.artwork.ArtworkService
import com.moviecatalog.movie.artwork.ArtworkUploadObserver
import com.moviecatalog.movie.artwork.streamArtwork
import com.moviecatalog.movie.artwork.toInfo
import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.PageSpec
import com.moviecatalog.support.parseUuid
import com.moviecatalog.support.reply
import com.moviecatalog.support.toProto
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component
import com.moviecatalog.contracts.movie.v1.Credit as CreditMessage
import com.moviecatalog.contracts.movie.v1.Movie as MovieMessage
import com.moviecatalog.contracts.movie.v1.MovieSummary as MovieSummaryMessage

fun Movie.toMessage(creditCount: Long, artwork: Artwork? = null): MovieMessage = MovieMessage.newBuilder()
    .setId(id.toString()).setTitle(title).setReleaseYear(releaseYear).setSynopsis(synopsis)
    .addAllGenres(genres.sortedBy { it.ordinal }.map { it.message })
    .setCreatedAt(createdAt.toProto()).setUpdatedAt(updatedAt.toProto())
    .setCreditCount(creditCount.toInt())
    .also { b -> runtimeMinutes?.let { b.setRuntimeMinutes(it) } }
    .also { b -> artwork?.let { b.setArtwork(it.toInfo()) } }
    .build()

fun MovieView.toMessage(): MovieMessage = movie.toMessage(creditCount, artwork)

fun Movie.toSummary(artwork: Artwork? = null): MovieSummaryMessage = MovieSummaryMessage.newBuilder()
    .setId(id.toString()).setTitle(title).setReleaseYear(releaseYear)
    .setHasArtwork(artwork != null).setArtworkVersion(artwork?.version ?: "").build()

fun Credit.toMessage(): CreditMessage = CreditMessage.newBuilder()
    .setId(id.toString()).setMovieId(movieId.toString()).setPersonId(personId.toString()).setRole(role.message)
    .also { b -> characterName?.let { b.setCharacterName(it) } }
    .build()

/** gRPC adapter for movies and credits: parses and answers; the rules live in the services. */
@Component
class MovieGrpcService(
    private val movies: MovieService,
    private val credits: CreditService,
    private val artwork: ArtworkService,
    private val ingest: ArtworkIngest,
) : MovieServiceGrpc.MovieServiceImplBase() {
    private fun fields(input: MovieInput) = MovieFields(
        input.title, input.releaseYear,
        if (input.hasRuntimeMinutes()) input.runtimeMinutes else null,
        input.synopsis, input.genresList.map { Genre.from(it) },
    )

    override fun createMovie(request: CreateMovieRequest, observer: StreamObserver<MovieMessage>) = observer.reply {
        movies.create(fields(request.input)).toMessage()
    }

    override fun getMovie(request: GetMovieRequest, observer: StreamObserver<MovieMessage>) = observer.reply {
        movies.get(parseUuid(request.id)).toMessage()
    }

    override fun updateMovie(request: UpdateMovieRequest, observer: StreamObserver<MovieMessage>) = observer.reply {
        val id = parseUuid(request.id)
        movies.update(id, fields(request.input)).toMessage()
    }

    override fun deleteMovie(request: DeleteMovieRequest, observer: StreamObserver<DeleteMovieResponse>) = observer.reply {
        val id = parseUuid(request.id)
        movies.delete(id)
        DeleteMovieResponse.newBuilder().setId(id.toString()).build()
    }

    override fun listMovies(request: ListMoviesRequest, observer: StreamObserver<ListMoviesResponse>) = observer.reply {
        val page = PageSpec.of(if (request.hasPage()) request.page else null)
        val sort = when (request.sortField) {
            MovieSortField.MOVIE_SORT_FIELD_UNSPECIFIED, MovieSortField.MOVIE_SORT_FIELD_TITLE -> MovieSort.TITLE
            MovieSortField.MOVIE_SORT_FIELD_RELEASE_YEAR -> MovieSort.RELEASE_YEAR
            else -> throw GrpcErrors.invalidArgument("sort" to "sort.invalid")
        }
        val genre = if (request.hasGenre()) Genre.from(request.genre) ?: throw GrpcErrors.invalidArgument("genre" to "movie.genres.invalid") else null
        val slice = movies.list(
            genre,
            if (request.hasYearFrom()) request.yearFrom else null,
            if (request.hasYearTo()) request.yearTo else null,
            sort,
            request.sortDirection == SortDirection.SORT_DIRECTION_DESC,
            page,
            genres = request.genresList.map { Genre.from(it) }, // null for unknown values; the service rejects them
        )
        ListMoviesResponse.newBuilder()
            .addAllMovies(slice.items.map { it.toMessage() })
            .setPageInfo(page.info(slice.total)).build()
    }

    override fun searchMovies(request: SearchMoviesRequest, observer: StreamObserver<ListMoviesResponse>) = observer.reply {
        val page = PageSpec.of(if (request.hasPage()) request.page else null)
        val slice = movies.search(request.query, page)
        ListMoviesResponse.newBuilder()
            .addAllMovies(slice.items.map { it.toMessage() })
            .setPageInfo(page.info(slice.total)).build()
    }

    override fun addCredit(request: AddCreditRequest, observer: StreamObserver<CreditMessage>) = observer.reply {
        val movieId = parseUuid(request.movieId, "movieId")
        val personId = parseUuid(request.personId, "personId")
        credits.add(movieId, personId, CreditRole.from(request.role), if (request.hasCharacterName()) request.characterName else null).toMessage()
    }

    override fun updateCredit(request: UpdateCreditRequest, observer: StreamObserver<CreditMessage>) = observer.reply {
        val id = parseUuid(request.creditId, "creditId")
        credits.update(id, CreditRole.from(request.role), if (request.hasCharacterName()) request.characterName else null).toMessage()
    }

    override fun removeCredit(request: RemoveCreditRequest, observer: StreamObserver<RemoveCreditResponse>) = observer.reply {
        val id = parseUuid(request.creditId, "creditId")
        credits.remove(id)
        RemoveCreditResponse.newBuilder().setCreditId(id.toString()).build()
    }

    override fun listCreditsByMovie(request: ListCreditsByMovieRequest, observer: StreamObserver<ListCreditsByMovieResponse>) = observer.reply {
        val result = credits.forMovie(parseUuid(request.movieId, "movieId"), request.limit)
        ListCreditsByMovieResponse.newBuilder()
            .addAllCredits(result.credits.map { it.toMessage() }).setTotal(result.total.toInt()).setTruncated(result.truncated).build()
    }

    override fun listCreditsByPerson(request: ListCreditsByPersonRequest, observer: StreamObserver<ListCreditsByPersonResponse>) = observer.reply {
        val personId = parseUuid(request.personId, "personId")
        val page = PageSpec.of(if (request.hasPage()) request.page else null)
        val slice = credits.filmography(personId, page)
        val art = artwork.forMovies(slice.items.map { it.movie.id }.distinct())
        ListCreditsByPersonResponse.newBuilder()
            .addAllCredits(slice.items.map { CreditWithMovie.newBuilder().setCredit(it.credit.toMessage()).setMovie(it.movie.toSummary(art[it.movie.id])).build() })
            .setPageInfo(page.info(slice.total)).build()
    }

    override fun checkPersonCredits(request: CheckPersonCreditsRequest, observer: StreamObserver<CheckPersonCreditsResponse>) = observer.reply {
        val result = credits.checkPerson(parseUuid(request.personId, "personId"), request.maxMovies)
        val art = artwork.forMovies(result.blockingMovies.map { it.id })
        CheckPersonCreditsResponse.newBuilder()
            .setCreditCount(result.count).addAllBlockingMovies(result.blockingMovies.map { it.toSummary(art[it.id]) }).build()
    }

    override fun uploadArtwork(observer: StreamObserver<ArtworkInfo>): StreamObserver<UploadArtworkRequest> =
        ArtworkUploadObserver(observer, ingest, artwork)

    override fun removeArtwork(request: RemoveArtworkRequest, observer: StreamObserver<RemoveArtworkResponse>) = observer.reply {
        val id = parseUuid(request.movieId, "movieId")
        artwork.remove(id)
        RemoveArtworkResponse.newBuilder().setMovieId(id.toString()).build()
    }

    override fun downloadArtwork(request: DownloadArtworkRequest, observer: StreamObserver<DownloadArtworkResponse>) =
        streamArtwork(artwork, request, observer)
}
