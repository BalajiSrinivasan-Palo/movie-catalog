package com.moviecatalog.people

import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.PageSpec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class PersonService(
    private val people: PersonRepository,
    private val queries: PersonQueries,
    private val movieCredits: MovieCredits,
    private val clock: Clock,
) {
    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneOffset.UTC))

    @Transactional
    fun create(name: String, birthDate: LocalDate?, malformedDate: Boolean, bio: String): Person {
        val valid = PersonValidator.validate(name, birthDate, malformedDate, bio, today())
        val now = Instant.now(clock)
        return people.save(Person(UUID.randomUUID(), valid.name, nameKeyOf(valid.name), valid.birthDate, valid.bio, now, now))
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Person = people.findById(id).orElseThrow { GrpcErrors.notFound("person.not_found") }

    /** Full replacement of every editable field (last write wins, BR-12). */
    @Transactional
    fun update(id: UUID, name: String, birthDate: LocalDate?, malformedDate: Boolean, bio: String): Person {
        val valid = PersonValidator.validate(name, birthDate, malformedDate, bio, today())
        val person = people.findById(id).orElseThrow { GrpcErrors.notFound("person.not_found") }
        person.name = valid.name
        person.nameKey = nameKeyOf(valid.name)
        person.birthDate = valid.birthDate
        person.bio = valid.bio
        person.updatedAt = Instant.now(clock)
        return people.save(person)
    }

    /**
     * Deleting is refused while the person has credits. The credit check lives in movie-service (D7, D8);
     * if it cannot be answered the delete fails closed rather than risking orphaned credits. The gap between
     * check and delete is a documented limitation (BR-13).
     */
    @Transactional
    fun delete(id: UUID) {
        val person = people.findById(id).orElseThrow { GrpcErrors.notFound("person.not_found") }
        val check = try {
            movieCredits.check(id)
        } catch (e: CreditCheckUnavailable) {
            throw GrpcErrors.unavailable("person.delete.check_unavailable")
        }
        if (check.creditCount > 0) {
            throw GrpcErrors.failedPrecondition(
                "person.has_credits", check.blockingMovies, mapOf("creditCount" to check.creditCount.toString()),
            )
        }
        people.delete(person)
    }

    @Transactional(readOnly = true)
    fun list(nameContains: String?, descending: Boolean, page: PageSpec): Slice<Person> {
        val filter = nameContains?.trim()
        if (filter != null && filter.length !in 1..100) throw GrpcErrors.invalidArgument("query" to "search.query.invalid")
        return queries.page(filter, descending, page)
    }

    /** Case-insensitive substring search on name and bio; 2..100 characters, wildcards literal (D9, EC-12). */
    @Transactional(readOnly = true)
    fun search(query: String, page: PageSpec): Slice<Person> {
        val q = query.trim()
        if (q.length !in 2..100) throw GrpcErrors.invalidArgument("query" to "search.query.invalid")
        return queries.page(q, false, page, alsoBio = true)
    }

    /** Unknown ids are left out of the result, so one deleted person never fails a movie page (BR-07). */
    @Transactional(readOnly = true)
    fun batchGet(ids: Collection<UUID>): List<Person> = if (ids.isEmpty()) emptyList() else people.findAllById(ids)

    companion object {
        const val MAX_BATCH = 200
    }
}
