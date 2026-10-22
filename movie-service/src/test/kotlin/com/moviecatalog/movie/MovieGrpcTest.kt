package com.moviecatalog.movie

import com.moviecatalog.contracts.common.v1.PageRequest
import com.moviecatalog.contracts.movie.v1.AddCreditRequest
import com.moviecatalog.contracts.movie.v1.CheckPersonCreditsRequest
import com.moviecatalog.contracts.movie.v1.CreateMovieRequest
import com.moviecatalog.contracts.movie.v1.CreditRole as CreditRoleMessage
import com.moviecatalog.contracts.movie.v1.GetMovieRequest
import com.moviecatalog.contracts.movie.v1.Genre as GenreMessage
import com.moviecatalog.contracts.movie.v1.ListMoviesRequest
import com.moviecatalog.contracts.movie.v1.MovieInput
import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.contracts.movie.v1.MovieSortField
import com.moviecatalog.support.GrpcErrors
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Wire-level contract checks through a real gRPC channel (NFR-TEST-03). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MovieGrpcTest {
    @Autowired lateinit var impl: MovieGrpcService
    @Autowired lateinit var movieRepository: MovieRepository
    @Autowired lateinit var creditRepository: CreditRepository
    private lateinit var stub: MovieServiceGrpc.MovieServiceBlockingStub
    private val closers = mutableListOf<() -> Unit>()

    @BeforeTest
    fun start() {
        creditRepository.deleteAll(); movieRepository.deleteAll()
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        closers += { channel.shutdownNow(); server.shutdownNow() }
        stub = MovieServiceGrpc.newBlockingStub(channel)
    }

    @AfterTest
    fun stop() = closers.forEach { it() }

    private fun input(title: String, year: Int = 2000, runtime: Int? = null) =
        MovieInput.newBuilder().setTitle(title).setReleaseYear(year).addGenres(GenreMessage.GENRE_DRAMA)
            .also { b -> runtime?.let { b.setRuntimeMinutes(it) } }.build()

    @Test
    fun `create returns the movie and optional runtime is absent when not given`() {
        val withRuntime = stub.createMovie(CreateMovieRequest.newBuilder().setInput(input("A", runtime = 90)).build())
        assertTrue(withRuntime.hasRuntimeMinutes())
        assertEquals(listOf(GenreMessage.GENRE_DRAMA), withRuntime.genresList)
        val without = stub.createMovie(CreateMovieRequest.newBuilder().setInput(input("B")).build())
        assertFalse(without.hasRuntimeMinutes())
        assertEquals(0, without.creditCount)
    }

    @Test
    fun `validation and duplicate failures carry ErrorDetail`() {
        val invalid = assertFailsWith<StatusRuntimeException> {
            stub.createMovie(CreateMovieRequest.newBuilder().setInput(input("", 1800)).build())
        }
        assertEquals(Status.Code.INVALID_ARGUMENT, invalid.status.code)
        assertEquals(
            listOf("title" to "movie.title.invalid", "releaseYear" to "movie.year.invalid"),
            GrpcErrors.detailOf(invalid)!!.violationsList.map { it.field to it.messageKey },
        )
        stub.createMovie(CreateMovieRequest.newBuilder().setInput(input("Dup")).build())
        val dup = assertFailsWith<StatusRuntimeException> { stub.createMovie(CreateMovieRequest.newBuilder().setInput(input("dup")).build()) }
        assertEquals(Status.Code.ALREADY_EXISTS, dup.status.code)
        assertEquals("movie.duplicate", GrpcErrors.detailOf(dup)!!.code)
    }

    @Test
    fun `malformed ids and unknown ids`() {
        val bad = assertFailsWith<StatusRuntimeException> { stub.getMovie(GetMovieRequest.newBuilder().setId("zzz").build()) }
        assertEquals("id.invalid", GrpcErrors.detailOf(bad)!!.violationsList.single().messageKey)
        val missing = assertFailsWith<StatusRuntimeException> {
            stub.getMovie(GetMovieRequest.newBuilder().setId(UUID.randomUUID().toString()).build())
        }
        assertEquals(Status.Code.NOT_FOUND, missing.status.code)
    }

    @Test
    fun `list defaults, paging bounds and sort`() {
        listOf("B" to 2001, "A" to 2002).forEach { (t, y) -> stub.createMovie(CreateMovieRequest.newBuilder().setInput(input(t, y)).build()) }
        val byTitle = stub.listMovies(ListMoviesRequest.getDefaultInstance())
        assertEquals(listOf("A", "B"), byTitle.moviesList.map { it.title })
        assertEquals(20, byTitle.pageInfo.size)
        val byYear = stub.listMovies(ListMoviesRequest.newBuilder().setSortField(MovieSortField.MOVIE_SORT_FIELD_RELEASE_YEAR).build())
        assertEquals(listOf("B", "A"), byYear.moviesList.map { it.title })
        val e = assertFailsWith<StatusRuntimeException> {
            stub.listMovies(ListMoviesRequest.newBuilder().setPage(PageRequest.newBuilder().setPage(0).setSize(500)).build())
        }
        assertEquals("paging.invalid", GrpcErrors.detailOf(e)!!.violationsList.single().messageKey)
    }

    @Test
    fun `S-702 AC8 AC9 listMovies reads the genres list, combines it with genre, and rejects bad lists`() {
        fun create(title: String, vararg genres: GenreMessage) = stub.createMovie(CreateMovieRequest.newBuilder().setInput(
            input(title).toBuilder().clearGenres().addAllGenres(genres.toList()).build()).build())
        create("A", GenreMessage.GENRE_ACTION)
        create("B", GenreMessage.GENRE_COMEDY, GenreMessage.GENRE_CRIME)
        create("C", GenreMessage.GENRE_CRIME)
        fun titles(request: ListMoviesRequest.Builder) = stub.listMovies(request.build()).moviesList.map { it.title }

        assertEquals(listOf("B", "C"), titles(ListMoviesRequest.newBuilder()
            .addGenres(GenreMessage.GENRE_COMEDY).addGenres(GenreMessage.GENRE_CRIME)))
        assertEquals(listOf("A", "B", "C"), titles(ListMoviesRequest.newBuilder()
            .setGenre(GenreMessage.GENRE_ACTION).addGenres(GenreMessage.GENRE_CRIME)))
        assertEquals(listOf("A"), titles(ListMoviesRequest.newBuilder().setGenre(GenreMessage.GENRE_ACTION))) // older clients

        fun rejected(request: ListMoviesRequest.Builder): List<Pair<String, String>> {
            val e = assertFailsWith<StatusRuntimeException> { stub.listMovies(request.build()) }
            assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
            return GrpcErrors.detailOf(e)!!.violationsList.map { it.field to it.messageKey }
        }
        val bad = listOf("genres" to "movie.genres.invalid")
        assertEquals(bad, rejected(ListMoviesRequest.newBuilder().addGenres(GenreMessage.GENRE_WAR).addGenres(GenreMessage.GENRE_WAR)))
        assertEquals(bad, rejected(ListMoviesRequest.newBuilder().addGenres(GenreMessage.GENRE_UNSPECIFIED)))
        assertEquals(bad, rejected(ListMoviesRequest.newBuilder().addGenresValue(999))) // unknown value on the wire
    }

    @Test
    fun `credits round-trip and the person check sees them`() {
        val movie = stub.createMovie(CreateMovieRequest.newBuilder().setInput(input("Film")).build())
        val person = UUID.randomUUID().toString()
        val credit = stub.addCredit(
            AddCreditRequest.newBuilder().setMovieId(movie.id).setPersonId(person)
                .setRole(CreditRoleMessage.CREDIT_ROLE_ACTOR).setCharacterName("Lead").build(),
        )
        assertEquals("Lead", credit.characterName)
        val check = stub.checkPersonCredits(CheckPersonCreditsRequest.newBuilder().setPersonId(person).build())
        assertEquals(1, check.creditCount)
        assertEquals("Film", check.blockingMoviesList.single().title)
        assertEquals(1, stub.getMovie(GetMovieRequest.newBuilder().setId(movie.id).build()).creditCount)
        val unspecified = assertFailsWith<StatusRuntimeException> {
            stub.addCredit(AddCreditRequest.newBuilder().setMovieId(movie.id).setPersonId(person).build())
        }
        assertEquals("credit.role.invalid", GrpcErrors.detailOf(unspecified)!!.violationsList.single().messageKey)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("movie-grpc-test")
            registry.add("MOVIE_DB_PATH") { dir.resolve("db").toString() }
            registry.add("grpc.server.port") { "0" }
            registry.add("ARTWORK_DIR") { dir.resolve("artwork").toString() }
        }
    }
}
