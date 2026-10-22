package com.moviecatalog.people

import com.moviecatalog.contracts.common.v1.Date
import com.moviecatalog.contracts.common.v1.PageRequest
import com.moviecatalog.contracts.people.v1.BatchGetPeopleRequest
import com.moviecatalog.contracts.people.v1.CreatePersonRequest
import com.moviecatalog.contracts.people.v1.GetPersonRequest
import com.moviecatalog.contracts.people.v1.ListPeopleRequest
import com.moviecatalog.contracts.people.v1.PeopleServiceGrpc
import com.moviecatalog.contracts.people.v1.PersonInput
import com.moviecatalog.support.GrpcErrors
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Contract-level checks through a real gRPC channel: wire shapes and ErrorDetail (NFR-TEST-03). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(FakeMovieCreditsConfig::class)
class PeopleGrpcTest {
    @Autowired lateinit var impl: PeopleGrpcService
    @Autowired lateinit var repository: PersonRepository
    private lateinit var stub: PeopleServiceGrpc.PeopleServiceBlockingStub
    private val closers = mutableListOf<() -> Unit>()

    @BeforeTest
    fun start() {
        repository.deleteAll()
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        closers += { channel.shutdownNow(); server.shutdownNow() }
        stub = PeopleServiceGrpc.newBlockingStub(channel)
    }

    @AfterTest
    fun stop() = closers.forEach { it() }

    private fun input(name: String, date: Date? = null) =
        PersonInput.newBuilder().setName(name).also { b -> date?.let { b.setBirthDate(it) } }.build()

    @Test
    fun `create then get round-trips including the optional birth date`() {
        val created = stub.createPerson(
            CreatePersonRequest.newBuilder().setInput(input("Ada", Date.newBuilder().setYear(1906).setMonth(12).setDay(9).build())).build(),
        )
        val loaded = stub.getPerson(GetPersonRequest.newBuilder().setId(created.id).build())
        assertEquals("Ada", loaded.name)
        assertEquals(1906, loaded.birthDate.year)
        val noDate = stub.createPerson(CreatePersonRequest.newBuilder().setInput(input("NoDate")).build())
        assertEquals(false, noDate.hasBirthDate())
    }

    @Test
    fun `a malformed id and an impossible date are INVALID_ARGUMENT with keys`() {
        val badId = assertFailsWith<StatusRuntimeException> { stub.getPerson(GetPersonRequest.newBuilder().setId("nope").build()) }
        assertEquals(Status.Code.INVALID_ARGUMENT, badId.status.code)
        assertEquals("id.invalid", GrpcErrors.detailOf(badId)!!.violationsList.single().messageKey)

        val badDate = assertFailsWith<StatusRuntimeException> {
            stub.createPerson(CreatePersonRequest.newBuilder().setInput(input("X", Date.newBuilder().setYear(2000).setMonth(2).setDay(31).build())).build())
        }
        assertEquals("person.birthdate.invalid", GrpcErrors.detailOf(badDate)!!.violationsList.single().messageKey)
    }

    @Test
    fun `list validates paging and sorting`() {
        val badPage = assertFailsWith<StatusRuntimeException> {
            stub.listPeople(ListPeopleRequest.newBuilder().setPage(PageRequest.newBuilder().setPage(-1).setSize(10)).build())
        }
        assertEquals("paging.invalid", GrpcErrors.detailOf(badPage)!!.violationsList.single().messageKey)
        val tooBig = assertFailsWith<StatusRuntimeException> {
            stub.listPeople(ListPeopleRequest.newBuilder().setPage(PageRequest.newBuilder().setPage(0).setSize(101)).build())
        }
        assertEquals(Status.Code.INVALID_ARGUMENT, tooBig.status.code)
        val defaults = stub.listPeople(ListPeopleRequest.getDefaultInstance())
        assertEquals(20, defaults.pageInfo.size)
    }

    @Test
    fun `batch get rejects malformed ids and returns known people`() {
        val a = stub.createPerson(CreatePersonRequest.newBuilder().setInput(input("Known")).build())
        val result = stub.batchGetPeople(
            BatchGetPeopleRequest.newBuilder().addIds(a.id).addIds("3f2c1a9e-7b44-4d1e-9a10-0c8d6e5f4a3b").build(),
        )
        assertEquals(listOf("Known"), result.peopleList.map { it.name })
        val e = assertFailsWith<StatusRuntimeException> { stub.batchGetPeople(BatchGetPeopleRequest.newBuilder().addIds("x").build()) }
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("people-grpc-test")
            registry.add("PEOPLE_DB_PATH") { dir.resolve("db").toString() }
            registry.add("grpc.server.port") { "0" }
        }
    }
}
