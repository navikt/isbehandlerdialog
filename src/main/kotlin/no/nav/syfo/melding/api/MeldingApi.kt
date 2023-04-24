package no.nav.syfo.melding.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.VeilederTilgangskontrollPlugin
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*
import java.util.UUID

const val meldingApiBasePath = "/api/internad/v1/melding"
const val uuid = "uuid"
const val vedleggNumber = "vedleggNumber"

private const val API_ACTION = "access behandlerdialog for person"

fun Route.registerMeldingApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    meldingService: MeldingService,
) {
    route(meldingApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }

        get {
            val personIdent = call.personIdent()
            val conversations = meldingService.getConversations(personIdent)

            call.respond(
                MeldingResponseDTO(conversations = conversations)
            )
        }

        get("/{$uuid}/{$vedleggNumber}/pdf") {
            val meldingUuid = UUID.fromString(call.parameters[uuid])
                ?: throw IllegalArgumentException("Missing value for uuid")
            val vedleggNumberString = call.parameters[vedleggNumber]
                ?: throw IllegalArgumentException("Missing value for vedleggNumber")
            val pdfContent = meldingService.getVedlegg(
                uuid = meldingUuid,
                vedleggNumber = vedleggNumberString.toInt(),
            )
            if (pdfContent == null) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(pdfContent)
            }
        }

        post {
            val personIdent = call.personIdent()
            val requestDTO = call.receive<MeldingTilBehandlerRequestDTO>()

            meldingService.createMeldingTilBehandler(
                callId = getCallId(),
                meldingTilBehandler = requestDTO.toMeldingTilBehandler(personIdent),
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
