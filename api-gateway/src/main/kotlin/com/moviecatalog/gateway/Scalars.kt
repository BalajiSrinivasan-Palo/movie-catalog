package com.moviecatalog.gateway

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** Custom scalars declared in schema.graphqls. Without an implementation the schema does not load. */
@Configuration
class ScalarConfig {
    @Bean
    fun scalars(): RuntimeWiringConfigurer = RuntimeWiringConfigurer { wiring ->
        wiring.scalar(DATE).scalar(DATE_TIME).scalar(UPLOAD)
    }

    companion object {
        val DATE: GraphQLScalarType = GraphQLScalarType.newScalar().name("Date")
            .description("ISO-8601 calendar date, e.g. 1999-03-31")
            .coercing(IsoCoercing("Date", LocalDate::parse)).build()

        val DATE_TIME: GraphQLScalarType = GraphQLScalarType.newScalar().name("DateTime")
            .description("ISO-8601 instant in UTC")
            .coercing(IsoCoercing("DateTime", Instant::parse)).build()

        /**
         * The multipart route replaces the file in `variables` with a token and puts the real files in the
         * GraphQL context. A request that is not a multipart upload has no files there, so it cannot name one.
         */
        val UPLOAD: GraphQLScalarType = GraphQLScalarType.newScalar().name("Upload")
            .description("A file in a GraphQL multipart request")
            .coercing(object : Coercing<Any, Any> {
                override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): Any =
                    throw UnsupportedOperationException("Upload is input-only")
                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Any =
                    graphQLContext.get<Map<String, UploadedFile>>(UPLOADS_CONTEXT_KEY)?.get(input.toString())
                        ?: throw CoercingParseValueException("Upload must be sent as a multipart request")
                override fun parseLiteral(
                    input: Value<*>, variables: CoercedVariables, graphQLContext: GraphQLContext, locale: Locale,
                ): Any = throw CoercingParseLiteralException("Upload must be passed as a variable")
            }).build()
    }
}

private class IsoCoercing<T : Any>(private val name: String, private val parse: (String) -> T) : Coercing<T, String> {
    @Suppress("UNCHECKED_CAST")
    override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String =
        dataFetcherResult.toString()

    override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): T =
        try { parse(input.toString()) } catch (e: DateTimeParseException) {
            throw CoercingParseValueException("Invalid $name")
        }

    override fun parseLiteral(
        input: Value<*>, variables: CoercedVariables, graphQLContext: GraphQLContext, locale: Locale,
    ): T {
        val text = (input as? StringValue)?.value ?: throw CoercingParseLiteralException("Invalid $name")
        return try { parse(text) } catch (e: DateTimeParseException) { throw CoercingParseLiteralException("Invalid $name") }
    }
}
