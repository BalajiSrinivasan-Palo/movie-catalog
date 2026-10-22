package com.moviecatalog.people

import com.moviecatalog.contracts.common.v1.Date
import com.moviecatalog.contracts.common.v1.SortDirection
import com.moviecatalog.contracts.people.v1.BatchGetPeopleRequest
import com.moviecatalog.contracts.people.v1.BatchGetPeopleResponse
import com.moviecatalog.contracts.people.v1.CreatePersonRequest
import com.moviecatalog.contracts.people.v1.DeletePersonRequest
import com.moviecatalog.contracts.people.v1.DeletePersonResponse
import com.moviecatalog.contracts.people.v1.GetPersonRequest
import com.moviecatalog.contracts.people.v1.ListPeopleRequest
import com.moviecatalog.contracts.people.v1.ListPeopleResponse
import com.moviecatalog.contracts.people.v1.PeopleServiceGrpc
import com.moviecatalog.contracts.people.v1.PersonInput
import com.moviecatalog.contracts.people.v1.PersonSortField
import com.moviecatalog.contracts.people.v1.SearchPeopleRequest
import com.moviecatalog.contracts.people.v1.UpdatePersonRequest
import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.PageSpec
import com.moviecatalog.support.parseUuid
import com.moviecatalog.support.reply
import com.moviecatalog.support.toLocalDateOrNull
import com.moviecatalog.support.toProto
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component
import com.moviecatalog.contracts.people.v1.Person as PersonMessage

fun Person.toMessage(): PersonMessage = PersonMessage.newBuilder()
    .setId(id.toString()).setName(name).setBio(bio)
    .setCreatedAt(createdAt.toProto()).setUpdatedAt(updatedAt.toProto())
    .also { b -> birthDate?.let { b.setBirthDate(it.toProto()) } }
    .build()

/** gRPC adapter: parses and answers; all rules live in [PersonService]. */
@Component
class PeopleGrpcService(private val service: PersonService) : PeopleServiceGrpc.PeopleServiceImplBase() {
    private data class Fields(val name: String, val birthDate: java.time.LocalDate?, val malformed: Boolean, val bio: String)

    private fun fields(input: PersonInput): Fields {
        val date: Date? = if (input.hasBirthDate()) input.birthDate else null
        val parsed = date?.toLocalDateOrNull()
        return Fields(input.name, parsed, date != null && parsed == null, input.bio)
    }

    override fun createPerson(request: CreatePersonRequest, observer: StreamObserver<PersonMessage>) = observer.reply {
        val f = fields(request.input)
        service.create(f.name, f.birthDate, f.malformed, f.bio).toMessage()
    }

    override fun getPerson(request: GetPersonRequest, observer: StreamObserver<PersonMessage>) = observer.reply {
        service.get(parseUuid(request.id)).toMessage()
    }

    override fun updatePerson(request: UpdatePersonRequest, observer: StreamObserver<PersonMessage>) = observer.reply {
        val id = parseUuid(request.id)
        val f = fields(request.input)
        service.update(id, f.name, f.birthDate, f.malformed, f.bio).toMessage()
    }

    override fun deletePerson(request: DeletePersonRequest, observer: StreamObserver<DeletePersonResponse>) = observer.reply {
        val id = parseUuid(request.id)
        service.delete(id)
        DeletePersonResponse.newBuilder().setId(id.toString()).build()
    }

    override fun listPeople(request: ListPeopleRequest, observer: StreamObserver<ListPeopleResponse>) = observer.reply {
        val page = PageSpec.of(if (request.hasPage()) request.page else null)
        when (request.sortField) {
            PersonSortField.PERSON_SORT_FIELD_UNSPECIFIED, PersonSortField.PERSON_SORT_FIELD_NAME -> Unit
            else -> throw GrpcErrors.invalidArgument("sort" to "sort.invalid")
        }
        val slice = service.list(
            if (request.hasQuery()) request.query else null,
            request.sortDirection == SortDirection.SORT_DIRECTION_DESC,
            page,
        )
        ListPeopleResponse.newBuilder().addAllPeople(slice.items.map { it.toMessage() }).setPageInfo(page.info(slice.total)).build()
    }

    override fun searchPeople(request: SearchPeopleRequest, observer: StreamObserver<ListPeopleResponse>) = observer.reply {
        val page = PageSpec.of(if (request.hasPage()) request.page else null)
        val slice = service.search(request.query, page)
        ListPeopleResponse.newBuilder().addAllPeople(slice.items.map { it.toMessage() }).setPageInfo(page.info(slice.total)).build()
    }

    override fun batchGetPeople(request: BatchGetPeopleRequest, observer: StreamObserver<BatchGetPeopleResponse>) = observer.reply {
        if (request.idsCount > PersonService.MAX_BATCH) throw GrpcErrors.invalidArgument("ids" to "paging.invalid")
        val ids = request.idsList.map { parseUuid(it, "ids") }.toSet()
        BatchGetPeopleResponse.newBuilder().addAllPeople(service.batchGet(ids).map { it.toMessage() }).build()
    }
}
