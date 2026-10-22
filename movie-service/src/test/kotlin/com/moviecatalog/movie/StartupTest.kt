package com.moviecatalog.movie

import com.moviecatalog.support.GrpcServerLifecycle
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S-001: the context starts against an empty data directory and Liquibase applies the baseline (NFR-REL-04). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StartupTest {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var grpc: GrpcServerLifecycle

    @Test
    fun `liquibase applies the movie and credit changesets to an empty database`() {
        val ids = jdbc.queryForList("SELECT ID FROM DATABASECHANGELOG", String::class.java)
        assertEquals(listOf("001-movie", "002-credit", "003-artwork"), ids)
    }

    @Test
    fun `grpc server is running on a bound port`() {
        assertTrue(grpc.isRunning)
        assertTrue(grpc.boundPort > 0)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("movie-db-test")
            registry.add("MOVIE_DB_PATH") { dir.resolve("db").toString() }
            registry.add("grpc.server.port") { "0" }
            registry.add("ARTWORK_DIR") { dir.resolve("artwork").toString() }
        }
    }
}
