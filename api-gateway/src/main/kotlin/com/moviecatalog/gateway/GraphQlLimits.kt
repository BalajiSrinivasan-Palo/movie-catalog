package com.moviecatalog.gateway

import graphql.analysis.FieldComplexityCalculator
import graphql.analysis.FieldComplexityEnvironment
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Rejects abusively deep or wide queries before they run (NFR-SEC-05, S-004 AC5). */
@Configuration(proxyBeanMethods = false)
class GraphQlLimits {
    @Bean
    fun depthLimit(): Instrumentation = MaxQueryDepthInstrumentation(MAX_DEPTH)

    @Bean
    fun complexityLimit(): Instrumentation = MaxQueryComplexityInstrumentation(MAX_COMPLEXITY, PAGE_AWARE)

    companion object {
        const val MAX_DEPTH = 10
        const val MAX_COMPLEXITY = 1500
        private const val DEFAULT_PAGE_SIZE = 20
        private val PAGED_FIELDS = setOf("movies", "people", "searchMovies", "searchPeople", "filmography")

        /**
         * A list field costs its children once per item it may return, so asking for 100 movies with their credits
         * counts 100 times more than asking for one (each credit also triggers downstream work).
         * One such request is allowed; two aliased copies of it are not (S-604 security review M2).
         */
        val PAGE_AWARE = FieldComplexityCalculator { env, childComplexity ->
            (1 + childComplexity) * itemsPerPage(env).coerceAtLeast(1)
        }

        /**
         * How many items a field may return. `search(movies:, people:)` itself is not a list: its two page sizes
         * belong to its `movies` and `people` groups, so each group is counted once at its own size. Counting them
         * on `search` as well multiplied the groups twice (10 x 20 = 200 per item) and refused the app's own search.
         */
        private fun itemsPerPage(env: FieldComplexityEnvironment): Int {
            val name = env.field.name
            if (name == "search") return 1
            sizeOf(env.arguments, "page")?.let { return it }
            val parent = env.parentEnvironment
            if (parent?.field?.name == "search") return sizeOf(parent.arguments, name) ?: DEFAULT_PAGE_SIZE
            return if (name in PAGED_FIELDS) DEFAULT_PAGE_SIZE else 1
        }

        private fun sizeOf(args: Map<String, Any?>, key: String): Int? =
            ((args[key] as? Map<*, *>)?.get("size") as? Number)?.toInt()
    }
}
