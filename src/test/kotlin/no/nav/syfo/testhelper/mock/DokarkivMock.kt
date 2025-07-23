package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.testhelper.UserConstants

val response = JournalpostResponse(
    journalpostId = 1,
    journalpostferdigstilt = true,
    journalstatus = "status",
)
val conflictResponse = JournalpostResponse(
    journalpostId = 2,
    journalpostferdigstilt = true,
    journalstatus = "conflict",
)

suspend fun MockRequestHandleScope.dokarkivMockResponse(request: HttpRequestData): HttpResponseData {
    val eksternReferanseId = request.receiveBody<JournalpostRequest>().eksternReferanseId

    return when (eksternReferanseId) {
        UserConstants.EXISTING_EKSTERN_REFERANSE_UUID -> respond(conflictResponse, HttpStatusCode.Conflict)
        else -> respond(response)
    }
}
