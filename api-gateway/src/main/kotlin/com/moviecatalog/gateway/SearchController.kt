package com.moviecatalog.gateway

import com.moviecatalog.contracts.movie.v1.SearchMoviesRequest
import com.moviecatalog.contracts.people.v1.SearchPeopleRequest
import jakarta.annotation.PreDestroy
import org.slf4j.MDC
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Search (FR-SRC-01..04). `search` asks both services at the same time and returns two groups; the two
 * single-group queries let the UI load "show more" for one group only (S-501 AC3). The services validate
 * the query (2 to 100 characters, wildcards literal), so the rule lives in one place.
 */
@Controller
class SearchController(
    private val downstream: Downstream,
    private val movieController: MovieController,
    private val peopleController: PeopleController,
) {
    // Virtual threads: each search waits on the network, so a thread per call is cheap.
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    @PreDestroy
    fun stop() = executor.shutdown()

    @QueryMapping
    fun search(
        @Argument query: String,
        @Argument(name = "movies") moviePage: PageInput?,
        @Argument(name = "people") peoplePage: PageInput?,
    ): SearchResult {
        val movies = inBackground { searchMovies(query, moviePage) }
        val people = inBackground { searchPeople(query, peoplePage) }
        // Both futures are always joined, so neither call is left running. The movie error wins if both fail.
        val movieResult = join(movies)
        val peopleResult = join(people)
        return SearchResult(movieResult.getOrThrow(), peopleResult.getOrThrow())
    }

    @QueryMapping
    fun searchMovies(@Argument query: String, @Argument page: PageInput?): MoviePage =
        movieController.pageOf(
            downstream.movie().searchMovies(SearchMoviesRequest.newBuilder().setQuery(query).setPage(page.toRequest()).build()),
        )

    @QueryMapping
    fun searchPeople(@Argument query: String, @Argument page: PageInput?): PersonPage =
        peopleController.pageOf(
            downstream.people().searchPeople(SearchPeopleRequest.newBuilder().setQuery(query).setPage(page.toRequest()).build()),
        )

    /** Runs [block] on another thread with the caller's logging context, so the correlation id reaches gRPC. */
    private fun <T> inBackground(block: () -> T): CompletableFuture<T> {
        val context = MDC.getCopyOfContextMap()
        return CompletableFuture.supplyAsync({
            if (context != null) MDC.setContextMap(context)
            try { block() } finally { MDC.clear() }
        }, executor)
    }

    private fun <T> join(future: CompletableFuture<T>): Result<T> =
        try { Result.success(future.get()) } catch (e: ExecutionException) { Result.failure(e.cause ?: e) }
}
