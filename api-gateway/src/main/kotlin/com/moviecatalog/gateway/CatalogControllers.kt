package com.moviecatalog.gateway

import com.moviecatalog.contracts.common.v1.SortDirection as SortDirectionMessage
import com.moviecatalog.contracts.movie.v1.AddCreditRequest
import com.moviecatalog.contracts.movie.v1.CreateMovieRequest
import com.moviecatalog.contracts.movie.v1.DeleteMovieRequest
import com.moviecatalog.contracts.movie.v1.GetMovieRequest
import com.moviecatalog.contracts.movie.v1.ListCreditsByMovieRequest
import com.moviecatalog.contracts.movie.v1.ListCreditsByPersonRequest
import com.moviecatalog.contracts.movie.v1.ListMoviesRequest
import com.moviecatalog.contracts.movie.v1.ListMoviesResponse
import com.moviecatalog.contracts.movie.v1.MovieInput as MovieInputMessage
import com.moviecatalog.contracts.movie.v1.MovieSortField as MovieSortFieldMessage
import com.moviecatalog.contracts.movie.v1.RemoveCreditRequest
import com.moviecatalog.contracts.movie.v1.UpdateCreditRequest
import com.moviecatalog.contracts.movie.v1.UpdateMovieRequest
import com.moviecatalog.contracts.people.v1.BatchGetPeopleRequest
import com.moviecatalog.contracts.people.v1.CreatePersonRequest
import com.moviecatalog.contracts.people.v1.DeletePersonRequest
import com.moviecatalog.contracts.people.v1.GetPersonRequest
import com.moviecatalog.contracts.people.v1.ListPeopleRequest
import com.moviecatalog.contracts.people.v1.ListPeopleResponse
import com.moviecatalog.contracts.people.v1.PersonInput as PersonInputMessage
import com.moviecatalog.contracts.people.v1.PersonSortField as PersonSortFieldMessage
import com.moviecatalog.contracts.people.v1.UpdatePersonRequest
import com.moviecatalog.support.toProto
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

private fun SortDirection.toMessage() =
    if (this == SortDirection.DESC) SortDirectionMessage.SORT_DIRECTION_DESC else SortDirectionMessage.SORT_DIRECTION_ASC

private fun isUnavailable(e: StatusRuntimeException) =
    e.status.code == Status.Code.UNAVAILABLE || e.status.code == Status.Code.DEADLINE_EXCEEDED

/** People: thin translation between GraphQL and people-service. Rules and validation live in the service. */
@Controller
class PeopleController(private val downstream: Downstream) {
    private fun personInput(input: PersonInput): PersonInputMessage = PersonInputMessage.newBuilder()
        .setName(input.name).setBio(input.bio).also { b -> input.birthDate?.let { b.setBirthDate(it.toProto()) } }.build()

    @QueryMapping
    fun person(@Argument id: String): PersonView =
        downstream.people().getPerson(GetPersonRequest.newBuilder().setId(id).build()).toView()

    @QueryMapping
    fun people(@Argument query: String?, @Argument sort: PersonSort?, @Argument page: PageInput?): PersonPage {
        val s = sort ?: PersonSort()
        val request = ListPeopleRequest.newBuilder().setPage(page.toRequest())
            .setSortField(PersonSortFieldMessage.PERSON_SORT_FIELD_NAME).setSortDirection(s.direction.toMessage())
            .also { b -> query?.let { b.setQuery(it) } }.build()
        return pageOf(downstream.people().listPeople(request))
    }

    fun pageOf(response: ListPeopleResponse) = PersonPage(response.peopleList.map { it.toView() }, response.pageInfo.toView())

    @MutationMapping
    fun createPerson(@Argument input: PersonInput): PersonView =
        downstream.people().createPerson(CreatePersonRequest.newBuilder().setInput(personInput(input)).build()).toView()

    @MutationMapping
    fun updatePerson(@Argument id: String, @Argument input: PersonInput): PersonView =
        downstream.people().updatePerson(UpdatePersonRequest.newBuilder().setId(id).setInput(personInput(input)).build()).toView()

    @MutationMapping
    fun deletePerson(@Argument id: String): String =
        downstream.people().deletePerson(DeletePersonRequest.newBuilder().setId(id).build()).id

    /** A person's credits, newest movie first (FR-PPL-05). Resolved only when the query asks for it. */
    @SchemaMapping(typeName = "Person", field = "filmography")
    fun filmography(person: PersonView, @Argument page: PageInput?): FilmographyPage {
        val response = downstream.movie().listCreditsByPerson(
            ListCreditsByPersonRequest.newBuilder().setPersonId(person.id).setPage(page.toRequest()).build(),
        )
        return FilmographyPage(
            response.creditsList.map {
                FilmographyEntry(it.credit.id, it.credit.role.toGraphQl(), if (it.credit.hasCharacterName()) it.credit.characterName else null, it.movie.toView())
            },
            response.pageInfo.toView(),
        )
    }
}

/** Movies and credits. Credit names are resolved from people-service in one batched call (NFR-PERF-04). */
@Controller
class MovieController(private val downstream: Downstream) {
    private fun movieInput(input: MovieInput): MovieInputMessage = MovieInputMessage.newBuilder()
        .setTitle(input.title).setReleaseYear(input.releaseYear).setSynopsis(input.synopsis)
        .addAllGenres(input.genres.map { it.toMessage() })
        .also { b -> input.runtimeMinutes?.let { b.setRuntimeMinutes(it) } }.build()

    private fun view(movie: com.moviecatalog.contracts.movie.v1.Movie): MovieView =
        movie.toView(lazy { loadCredits(movie.id) })

    @QueryMapping
    fun movie(@Argument id: String): MovieView =
        view(downstream.movie().getMovie(GetMovieRequest.newBuilder().setId(id).build()))

    @QueryMapping
    fun movies(@Argument filter: MovieFilter?, @Argument sort: MovieSort?, @Argument page: PageInput?): MoviePage {
        val s = sort ?: MovieSort()
        val request = ListMoviesRequest.newBuilder().setPage(page.toRequest())
            .setSortField(if (s.field == MovieSortField.TITLE) MovieSortFieldMessage.MOVIE_SORT_FIELD_TITLE else MovieSortFieldMessage.MOVIE_SORT_FIELD_RELEASE_YEAR)
            .setSortDirection(s.direction.toMessage())
            .also { b ->
                filter?.genre?.let { b.setGenre(it.toMessage()) }
                filter?.genres?.let { list -> b.addAllGenres(list.map { it.toMessage() }) }
                filter?.yearFrom?.let { b.setYearFrom(it) }
                filter?.yearTo?.let { b.setYearTo(it) }
            }.build()
        return pageOf(downstream.movie().listMovies(request))
    }

    fun pageOf(response: ListMoviesResponse) = MoviePage(response.moviesList.map(::view), response.pageInfo.toView())

    @MutationMapping
    fun createMovie(@Argument input: MovieInput): MovieView =
        view(downstream.movie().createMovie(CreateMovieRequest.newBuilder().setInput(movieInput(input)).build()))

    @MutationMapping
    fun updateMovie(@Argument id: String, @Argument input: MovieInput): MovieView =
        view(downstream.movie().updateMovie(UpdateMovieRequest.newBuilder().setId(id).setInput(movieInput(input)).build()))

    @MutationMapping
    fun deleteMovie(@Argument id: String): String =
        downstream.movie().deleteMovie(DeleteMovieRequest.newBuilder().setId(id).build()).id

    @SchemaMapping(typeName = "Movie", field = "credits")
    fun credits(movie: MovieView): List<CreditView> = movie.creditBundle.value.credits

    @SchemaMapping(typeName = "Movie", field = "creditsTruncated")
    fun creditsTruncated(movie: MovieView): Boolean = movie.creditBundle.value.truncated

    /**
     * Names come from people-service in one batch. If it is down the credits are still returned, with
     * `personStatus: UNAVAILABLE`, so the movie page degrades instead of failing (EC-15). An id that no longer
     * resolves is `NOT_FOUND` (BR-07).
     */
    private fun loadCredits(movieId: String): CreditBundle {
        val response = downstream.movie().listCreditsByMovie(
            ListCreditsByMovieRequest.newBuilder().setMovieId(movieId).setLimit(CREDIT_LIMIT).build(),
        )
        val ids = response.creditsList.map { it.personId }.distinct()
        var unavailable = false
        val people = if (ids.isEmpty()) emptyMap() else try {
            downstream.people().batchGetPeople(BatchGetPeopleRequest.newBuilder().addAllIds(ids).build())
                .peopleList.associate { it.id to it.toView() }
        } catch (e: StatusRuntimeException) {
            if (!isUnavailable(e)) throw e
            unavailable = true
            emptyMap()
        }
        val credits = response.creditsList.map { c ->
            val person = people[c.personId]
            when {
                person != null -> c.toView(PersonStatus.RESOLVED, person)
                unavailable -> c.toView(PersonStatus.UNAVAILABLE, null)
                else -> c.toView(PersonStatus.NOT_FOUND, null)
            }
        }
        return CreditBundle(credits, response.truncated)
    }

    /** Checks the person exists before adding the credit (CR-02); a missing person is a field error. */
    @MutationMapping
    fun addCredit(@Argument movieId: String, @Argument input: CreditInput): CreditView {
        val movie = parseId(movieId, "movieId").toString()
        val personId = parseId(input.personId, "personId").toString()
        val person = try {
            downstream.people().getPerson(GetPersonRequest.newBuilder().setId(personId).build()).toView()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) throw GatewayValidationException("personId", "credit.person.not_found")
            throw e
        }
        val request = AddCreditRequest.newBuilder().setMovieId(movie).setPersonId(personId).setRole(input.role.toMessage())
            .also { b -> input.characterName?.let { b.setCharacterName(it) } }.build()
        return downstream.movie().addCredit(request).toView(PersonStatus.RESOLVED, person)
    }

    @MutationMapping
    fun updateCredit(@Argument id: String, @Argument input: CreditUpdateInput): CreditView {
        val request = UpdateCreditRequest.newBuilder().setCreditId(id).setRole(input.role.toMessage())
            .also { b -> input.characterName?.let { b.setCharacterName(it) } }.build()
        val credit = downstream.movie().updateCredit(request)
        val person = try {
            downstream.people().getPerson(GetPersonRequest.newBuilder().setId(credit.personId).build()).toView()
        } catch (e: StatusRuntimeException) {
            return credit.toView(if (e.status.code == Status.Code.NOT_FOUND) PersonStatus.NOT_FOUND else PersonStatus.UNAVAILABLE, null)
        }
        return credit.toView(PersonStatus.RESOLVED, person)
    }

    @MutationMapping
    fun removeCredit(@Argument id: String): String =
        downstream.movie().removeCredit(RemoveCreditRequest.newBuilder().setCreditId(id).build()).creditId

    companion object {
        const val CREDIT_LIMIT = 200
    }
}
