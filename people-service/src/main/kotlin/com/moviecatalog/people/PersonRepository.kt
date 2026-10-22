package com.moviecatalog.people

import com.moviecatalog.support.PageSpec
import com.moviecatalog.support.escapeLike
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Locale
import java.util.UUID

interface PersonRepository : JpaRepository<Person, UUID>

/** Result of a paged query. */
data class Slice<T>(val items: List<T>, val total: Long)

/**
 * Name queries are built with the JPA Criteria API so every value is a bound parameter (NFR-SEC-04),
 * and the user's text is escaped so `%`, `_` and `\` match literally (EC-12).
 */
@Repository
class PersonQueries(private val em: EntityManager) {
    /**
     * [text] is matched as a substring of the name; with [alsoBio] it may match the bio instead (search, FR-SRC-01).
     * Both are lower-cased on both sides, so the match ignores case.
     */
    fun page(text: String?, descending: Boolean, page: PageSpec, alsoBio: Boolean = false): Slice<Person> {
        val cb = em.criteriaBuilder
        val pattern = text?.let { "%" + escapeLike(it.lowercase(Locale.ROOT)) + "%" }
        fun matches(root: Root<Person>): Predicate? = pattern?.let {
            val byName = cb.like(root.get("nameKey"), it, '\\')
            if (alsoBio) cb.or(byName, cb.like(cb.lower(root.get<String>("bio")), it, '\\')) else byName
        }

        val count = cb.createQuery(Long::class.javaObjectType)
        val countRoot = count.from(Person::class.java)
        count.select(cb.count(countRoot))
        matches(countRoot)?.let { count.where(it) }
        val total = em.createQuery(count).singleResult

        val query = cb.createQuery(Person::class.java)
        val root = query.from(Person::class.java)
        matches(root)?.let { query.where(it) }
        val primary = if (descending) cb.desc(root.get<String>("nameKey")) else cb.asc(root.get<String>("nameKey"))
        query.select(root).orderBy(primary, cb.asc(root.get<UUID>("id"))) // id keeps paging stable for equal names
        val items = em.createQuery(query).setFirstResult(page.offset).setMaxResults(page.size).resultList
        return Slice(items, total)
    }
}
