package com.moviecatalog.gateway

import graphql.schema.GraphQLSchema
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.execution.GraphQlSource
import kotlin.test.assertNotNull

/** S-001: the gateway context starts and the checked-in GraphQL schema loads with its custom scalars. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StartupTest {
    @Autowired lateinit var source: GraphQlSource

    @Test
    fun `schema loads with Date, DateTime and Upload scalars`() {
        val schema: GraphQLSchema = source.schema()
        listOf("Date", "DateTime", "Upload").forEach { assertNotNull(schema.getType(it), it) }
    }
}
