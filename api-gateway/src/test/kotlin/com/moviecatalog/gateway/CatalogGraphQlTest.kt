package com.moviecatalog.gateway

import com.moviecatalog.contracts.common.v1.BlockingMovie
import com.moviecatalog.contracts.common.v1.PageInfo as PageInfoMessage
import com.moviecatalog.contracts.movie.v1.AddCreditRequest
import com.moviecatalog.contracts.movie.v1.Credit
import com.moviecatalog.contracts.movie.v1.CreditRole as CreditRoleMessage
import com.moviecatalog.contracts.movie.v1.GetMovieRequest
import com.moviecatalog.contracts.movie.v1.Genre as GenreMessage
import com.moviecatalog.contracts.movie.v1.ListCreditsByMovieRequest
import com.moviecatalog.contracts.movie.v1.ListCreditsByMovieResponse
import com.moviecatalog.contracts.movie.v1.ListMoviesRequest
import com.moviecatalog.contracts.movie.v1.ListMoviesResponse
import com.moviecatalog.contracts.movie.v1.Movie
import com.moviecatalog.contracts.movie.v1.SearchMoviesRequest
import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.contracts.people.v1.BatchGetPeopleRequest
import com.moviecatalog.contracts.people.v1.BatchGetPeopleResponse
import com.moviecatalog.contracts.people.v1.CreatePersonRequest
import com.moviecatalog.contracts.people.v1.DeletePersonRequest
import com.moviecatalog.contracts.people.v1.DeletePersonResponse
import com.moviecatalog.contracts.people.v1.GetPersonRequest
import com.moviecatalog.contracts.people.v1.PeopleServiceGrpc
import com.moviecatalog.contracts.people.v1.ListPeopleResponse
import com.moviecatalog.contracts.people.v1.Person
import com.moviecatalog.contracts.people.v1.SearchPeopleRequest
import com.moviecatalog.support.GrpcErrors
import graphql.ExecutionInput
import io.grpc.ManagedChannel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.graphql.execution.GraphQlSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MOVIE_ID = "3f2c1a9e-7b44-4d1e-9a10-0c8d6e5f4a3b"
private const val ADA = "aaaaaaaa-0000-4000-8000-000000000001"
private const val GONE = "bbbbbbbb-0000-4000-8000-000000000002"

/** Fake backends the tests steer; real gRPC wire and the real GraphQL schema, resolvers and error mapping. */
class FakeBackends {
    @Volatile var peopleUp = true
    @Volatile var deleteBlocked = false
    val listMoviesCalls = AtomicInteger()
    @Volatile var lastListMovies: ListMoviesRequest? = null
    val adaPerson: Person = Person.newBuilder().setId(ADA).setName("Ada Lovelace").setBio("").build()

    val movie = object : MovieServiceGrpc.MovieServiceImplBase() {
        override fun getMovie(r: GetMovieRequest, o: StreamObserver<Movie>) {
            o.onNext(Movie.newBuilder().setId(r.id).setTitle("Heat").setReleaseYear(1995).setSynopsis("")
                .addGenres(GenreMessage.GENRE_SCI_FI).setCreditCount(2).build())
            o.onCompleted()
        }
        override fun listCreditsByMovie(r: ListCreditsByMovieRequest, o: StreamObserver<ListCreditsByMovieResponse>) {
            fun credit(id: String, person: String) = Credit.newBuilder().setId(id).setMovieId(r.movieId).setPersonId(person)
                .setRole(CreditRoleMessage.CREDIT_ROLE_ACTOR).setCharacterName("Lead").build()
            o.onNext(ListCreditsByMovieResponse.newBuilder().addCredits(credit("c1", ADA)).addCredits(credit("c2", GONE)).setTotal(2).build())
            o.onCompleted()
        }
        override fun listMovies(r: ListMoviesRequest, o: StreamObserver<ListMoviesResponse>) {
            listMoviesCalls.incrementAndGet()
            lastListMovies = r
            if (r.genresList.toSet().size != r.genresCount) { // stands in for movie-service's own check
                o.onError(GrpcErrors.invalidArgument("genres" to "movie.genres.invalid")); return
            }
            o.onNext(ListMoviesResponse.newBuilder()
                .setPageInfo(PageInfoMessage.newBuilder().setPage(r.page.page).setSize(r.page.size).setTotalElements(0).setTotalPages(0)).build())
            o.onCompleted()
        }
        override fun searchMovies(r: SearchMoviesRequest, o: StreamObserver<ListMoviesResponse>) {
            if (r.query.trim().length < 2) { o.onError(GrpcErrors.invalidArgument("query" to "search.query.invalid")); return }
            val hits = if (r.query.contains("heat", ignoreCase = true)) listOf(
                Movie.newBuilder().setId(MOVIE_ID).setTitle("Heat").setReleaseYear(1995).build(),
            ) else emptyList()
            o.onNext(ListMoviesResponse.newBuilder().addAllMovies(hits)
                .setPageInfo(PageInfoMessage.newBuilder().setPage(r.page.page).setSize(r.page.size).setTotalElements(hits.size.toLong()).setTotalPages(hits.size)).build())
            o.onCompleted()
        }
        override fun addCredit(r: AddCreditRequest, o: StreamObserver<Credit>) {
            o.onNext(Credit.newBuilder().setId("c9").setMovieId(r.movieId).setPersonId(r.personId).setRole(r.role).build())
            o.onCompleted()
        }
    }

    val people = object : PeopleServiceGrpc.PeopleServiceImplBase() {
        override fun getPerson(r: GetPersonRequest, o: StreamObserver<Person>) {
            if (r.id == ADA) { o.onNext(adaPerson); o.onCompleted() } else o.onError(GrpcErrors.notFound("person.not_found"))
        }
        override fun searchPeople(r: SearchPeopleRequest, o: StreamObserver<ListPeopleResponse>) {
            if (r.query.trim().length < 2) { o.onError(GrpcErrors.invalidArgument("query" to "search.query.invalid")); return }
            o.onNext(ListPeopleResponse.newBuilder().addPeople(adaPerson)
                .setPageInfo(PageInfoMessage.newBuilder().setPage(r.page.page).setSize(r.page.size).setTotalElements(1).setTotalPages(1)).build())
            o.onCompleted()
        }
        override fun batchGetPeople(r: BatchGetPeopleRequest, o: StreamObserver<BatchGetPeopleResponse>) {
            o.onNext(BatchGetPeopleResponse.newBuilder().addPeople(adaPerson).build()); o.onCompleted()
        }
        override fun createPerson(r: CreatePersonRequest, o: StreamObserver<Person>) {
            if (r.input.name.isBlank()) o.onError(GrpcErrors.invalidArgument("name" to "person.name.invalid"))
            else { o.onNext(Person.newBuilder().setId(ADA).setName(r.input.name).setBio(r.input.bio).also { if (r.input.hasBirthDate()) it.setBirthDate(r.input.birthDate) }.build()); o.onCompleted() }
        }
        override fun deletePerson(r: DeletePersonRequest, o: StreamObserver<DeletePersonResponse>) {
            if (deleteBlocked) {
                val m = BlockingMovie.newBuilder().setId(MOVIE_ID).setTitle("Heat").setReleaseYear(1995).build()
                o.onError(GrpcErrors.failedPrecondition("person.has_credits", listOf(m), mapOf("creditCount" to "2")))
            } else { o.onNext(DeletePersonResponse.newBuilder().setId(r.id).build()); o.onCompleted() }
        }
    }
}

@TestConfiguration
class FakeDownstreamConfig {
    private val backends = FakeBackends()
    private val closers = mutableListOf<() -> Unit>()

    @Bean fun fakeBackends() = backends

    @Bean @Primary
    fun fakeDownstream(): Downstream {
        fun channel(service: io.grpc.BindableService): ManagedChannel {
            val name = InProcessServerBuilder.generateName()
            val server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start()
            val ch = InProcessChannelBuilder.forName(name).directExecutor().build()
            closers += { ch.shutdownNow(); server.shutdownNow() }
            return ch
        }
        val movie = channel(backends.movie)
        // people: a channel that switches between the fake and "nothing there" per test
        val peopleChannel = channel(backends.people)
        val deadPeople = InProcessChannelBuilder.forName("no-such-server").directExecutor().build()
        val switching = object : io.grpc.Channel() {
            override fun <A, B> newCall(m: io.grpc.MethodDescriptor<A, B>, o: io.grpc.CallOptions) =
                (if (backends.peopleUp) peopleChannel else deadPeople).newCall(m, o)
            override fun authority(): String = "fake"
        }
        return Downstream(movie, switching, Deadlines(defaultMs = 2000))
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(FakeDownstreamConfig::class)
class CatalogGraphQlTest {
    @Autowired lateinit var source: GraphQlSource
    @Autowired lateinit var backends: FakeBackends

    @BeforeTest fun reset() { backends.peopleUp = true; backends.deleteBlocked = false; MDC.put("correlationId", "test-correlation-1") }
    @AfterTest fun clear() = MDC.clear()

    @Suppress("UNCHECKED_CAST")
    private fun run(query: String): Map<String, Any?> =
        source.graphQl().execute(ExecutionInput.newExecutionInput().query(query).build()).toSpecification() as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun errors(r: Map<String, Any?>) = (r["errors"] as? List<Map<String, Any?>>).orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun ext(r: Map<String, Any?>) = errors(r).single()["extensions"] as Map<String, Any?>

    private val movieQuery = """{ movie(id: "$MOVIE_ID") { title genres creditCount creditsTruncated
        credits { id personStatus characterName person { name } } } }"""

    @Suppress("UNCHECKED_CAST")
    private fun movieData(r: Map<String, Any?>) = (r["data"] as Map<String, Any?>)["movie"] as Map<String, Any?>

    @Test
    fun `movie page resolves names in one batch and marks missing people NOT_FOUND`() {
        val r = run(movieQuery)
        assertTrue(errors(r).isEmpty(), r.toString())
        val m = movieData(r)
        assertEquals(listOf("SCI_FI"), m["genres"])
        assertEquals(false, m["creditsTruncated"])
        @Suppress("UNCHECKED_CAST") val credits = m["credits"] as List<Map<String, Any?>>
        assertEquals(listOf("RESOLVED", "NOT_FOUND"), credits.map { it["personStatus"] })
        assertEquals("Ada Lovelace", (credits[0]["person"] as Map<*, *>)["name"])
        assertNull(credits[1]["person"])
    }

    @Test
    fun `movie page still works when people-service is down`() {
        backends.peopleUp = false
        val r = run(movieQuery)
        assertTrue(errors(r).isEmpty(), r.toString())
        @Suppress("UNCHECKED_CAST") val credits = movieData(r)["credits"] as List<Map<String, Any?>>
        assertEquals(listOf("UNAVAILABLE", "UNAVAILABLE"), credits.map { it["personStatus"] })
        assertEquals("Heat", movieData(r)["title"])
    }

    @Test
    fun `a malformed movie id from the service surfaces as VALIDATION_FAILED`() {
        // the fake movie service accepts any id, so check the gateway's own parser through addCredit
        val r = run("""mutation { addCredit(movieId: "nope", input: { personId: "$ADA", role: ACTOR }) { id } }""")
        val e = ext(r)
        assertEquals("VALIDATION_FAILED", e["code"])
        assertEquals("test-correlation-1", e["correlationId"])
        assertEquals(listOf(mapOf("field" to "movieId", "messageKey" to "id.invalid")), e["fieldErrors"])
    }

    @Test
    fun `adding a credit for an unknown person is a field error, a known person works`() {
        val missing = run("""mutation { addCredit(movieId: "$MOVIE_ID", input: { personId: "$GONE", role: ACTOR }) { id } }""")
        assertEquals(listOf(mapOf("field" to "personId", "messageKey" to "credit.person.not_found")), ext(missing)["fieldErrors"])
        val ok = run("""mutation { addCredit(movieId: "$MOVIE_ID", input: { personId: "$ADA", role: DIRECTOR }) { id role personStatus person { name } } }""")
        assertTrue(errors(ok).isEmpty(), ok.toString())
        @Suppress("UNCHECKED_CAST") val data = (ok["data"] as Map<String, Any?>)["addCredit"] as Map<String, Any?>
        assertEquals("DIRECTOR", data["role"])
        assertEquals("RESOLVED", data["personStatus"])
    }

    @Test
    fun `person input binds the Date scalar and defaults, and field errors come back keyed`() {
        val ok = run("""mutation { createPerson(input: { name: "Ada", birthDate: "1815-12-10" }) { name birthDate bio } }""")
        assertTrue(errors(ok).isEmpty(), ok.toString())
        @Suppress("UNCHECKED_CAST") val p = (ok["data"] as Map<String, Any?>)["createPerson"] as Map<String, Any?>
        assertEquals("1815-12-10", p["birthDate"])
        assertEquals("", p["bio"])
        val bad = run("""mutation { createPerson(input: { name: " " }) { name } }""")
        assertEquals(listOf(mapOf("field" to "name", "messageKey" to "person.name.invalid")), ext(bad)["fieldErrors"])
        val badDate = run("""mutation { createPerson(input: { name: "X", birthDate: "not-a-date" }) { name } }""")
        assertTrue(errors(badDate).isNotEmpty())
    }

    @Test
    fun `blocked person deletion is a CONFLICT with the blocking movies`() {
        backends.deleteBlocked = true
        val r = run("""mutation { deletePerson(id: "$ADA") }""")
        val e = ext(r)
        assertEquals("CONFLICT", e["code"])
        assertEquals("person.has_credits", e["messageKey"])
        @Suppress("UNCHECKED_CAST") val blocking = e["blockingMovies"] as List<Map<String, Any?>>
        assertEquals("Heat", blocking.single()["title"])
        backends.deleteBlocked = false
        assertEquals(ADA, (run("""mutation { deletePerson(id: "$ADA") }""")["data"] as Map<*, *>)["deletePerson"])
    }

    @Test
    fun `a downstream outage is UNAVAILABLE without internals`() {
        backends.peopleUp = false
        val r = run("""{ person(id: "$ADA") { name } }""")
        val e = ext(r)
        assertEquals("UNAVAILABLE", e["code"])
        assertTrue(!errors(r).single()["message"].toString().contains("no-such-server"))
    }

    private val searchQuery = """query(${'$'}q: String!) { search(query: ${'$'}q) {
        movies { items { id title } pageInfo { totalElements } } people { items { id name } pageInfo { totalElements } } } }"""

    private fun search(q: String): Map<String, Any?> = source.graphQl().execute(
        ExecutionInput.newExecutionInput().query(searchQuery).variables(mapOf("q" to q)).build(),
    ).toSpecification() as Map<String, Any?>

    @Test
    fun `search returns movies and people as two groups from two parallel calls`() {
        val r = search("heat")
        assertTrue(errors(r).isEmpty(), r.toString())
        @Suppress("UNCHECKED_CAST") val data = (r["data"] as Map<String, Any?>)["search"] as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST") val movies = data.getValue("movies")["items"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST") val people = data.getValue("people")["items"] as List<Map<String, Any?>>
        assertEquals(listOf("Heat"), movies.map { it["title"] })
        assertEquals(listOf("Ada Lovelace"), people.map { it["name"] })
    }

    @Test
    fun `a too-short query is a keyed validation error, not a server error`() {
        val r = search(" a ")
        assertEquals("VALIDATION_FAILED", ext(r)["code"])
        assertEquals("search.query.invalid", (ext(r)["fieldErrors"] as List<*>).map { (it as Map<*, *>)["messageKey"] }.first())
        assertEquals(1, errors(r).size)
    }

    @Test
    fun `when one backend is down the search fails with one UNAVAILABLE error`() {
        backends.peopleUp = false
        val r = search("heat")
        assertEquals(1, errors(r).size)
        assertEquals("UNAVAILABLE", ext(r)["code"])
    }

    @Test
    fun `S-702 AC8 the genres filter reaches movie-service as a list, alongside the deprecated genre`() {
        val r = run("""{ movies(filter: { genres: [COMEDY, CRIME], genre: ACTION, yearFrom: 1990 }) { items { id } pageInfo { totalElements } } }""")
        assertTrue(errors(r).isEmpty(), r.toString())
        val sent = backends.lastListMovies!!
        assertEquals(listOf(GenreMessage.GENRE_COMEDY, GenreMessage.GENRE_CRIME), sent.genresList)
        assertEquals(GenreMessage.GENRE_ACTION, sent.genre)
        assertEquals(1990, sent.yearFrom)

        val none = run("{ movies { items { id } } }")
        assertTrue(errors(none).isEmpty(), none.toString())
        assertEquals(0, backends.lastListMovies!!.genresCount)
        assertFalse(backends.lastListMovies!!.hasGenre())
    }

    @Test
    fun `S-702 AC9 a rejected genres list comes back as a keyed field error`() {
        val r = run("{ movies(filter: { genres: [WAR, WAR] }) { items { id } } }")
        val e = ext(r)
        assertEquals("VALIDATION_FAILED", e["code"])
        assertEquals(listOf(mapOf("field" to "genres", "messageKey" to "movie.genres.invalid")), e["fieldErrors"])
    }

    /** The exact selection the frontend sends (frontend/src/lib/api/catalog.ts, searchAll). */
    private val appSearch = """search(query: "heat", movies: { page: 0, size: 10 }, people: { page: 0, size: 10 }) {
        movies { items { id title releaseYear genres } pageInfo { page size totalElements totalPages } }
        people { items { id name birthDate bio } pageInfo { page size totalElements totalPages } } }"""

    @Test
    fun `the app's own grouped search is within the complexity limit, and wide copies of it are still refused`() {
        val one = run("{ $appSearch }")
        assertTrue(errors(one).isEmpty(), one.toString()) // was refused as 4410 > 1500 before the fix
        val wide = run("{ a: $appSearch b: $appSearch c: $appSearch d: $appSearch e: $appSearch f: $appSearch g: $appSearch }")
        assertTrue(errors(wide).single()["message"].toString().contains("complexity"), wide.toString())
    }

    @Test
    fun `single-group queries page one group only`() {
        val r = run("""{ searchMovies(query: "heat", page: { page: 1, size: 5 }) { items { title } pageInfo { page size } } }""")
        assertTrue(errors(r).isEmpty(), r.toString())
        @Suppress("UNCHECKED_CAST") val page = ((r["data"] as Map<String, Any?>)["searchMovies"] as Map<String, Any?>)["pageInfo"] as Map<String, Any?>
        assertEquals(1, page["page"])
        assertEquals(5, page["size"])
        val people = run("""{ searchPeople(query: "ad") { items { name } } }""")
        assertTrue(errors(people).isEmpty(), people.toString())
    }

    @Test
    fun `one large page is allowed but several aliased copies of it are refused before running`() {
        val fields = "items { id title genres artwork { url thumbnailUrl width height contentType sizeBytes } }"
        val one = run("{ a: movies(page: { page: 0, size: 100 }) { $fields } }")
        assertTrue(errors(one).isEmpty(), one.toString())
        val callsBefore = backends.listMoviesCalls.get()
        assertTrue(callsBefore > 0, "the allowed query must reach movie-service") // proves the counter counts
        val many = run("{ a: movies(page: { page: 0, size: 100 }) { $fields } b: movies(page: { page: 0, size: 100 }) { $fields } c: movies(page: { page: 0, size: 100 }) { $fields } }")
        val error = errors(many).single()
        assertEquals("ExecutionAborted", (error["extensions"] as Map<*, *>)["classification"], many.toString())
        assertTrue((error["message"] as String).contains("complexity"), many.toString())
        assertNull(many["data"])
        assertEquals(callsBefore, backends.listMoviesCalls.get(), "a refused query must not reach movie-service")
    }
}
