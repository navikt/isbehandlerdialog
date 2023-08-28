package no.nav.syfo.melding.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.checkVeilederTilgang
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
        get {
            val personIdent = call.personIdent()
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                personIdent = personIdent
            )
            val conversations = meldingService.getConversations(personIdent)

            call.respond(
                MeldingResponseDTO(conversations = conversations)
            )
        }

        get("/{$uuid}/{$vedleggNumber}/pdf") {
            val meldingUuid = call.meldingUuid()
            val vedleggNumberString = call.parameters[vedleggNumber]
                ?: throw IllegalArgumentException("Missing value for vedleggNumber")

            val personIdent = meldingService.getArbeidstakerPersonIdentForMelding(meldingUuid)
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                personIdent = personIdent
            )

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
            val veilederIdent = call.getNAVIdent()
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                personIdent = personIdent
            )
            val requestDTO = call.receive<MeldingTilBehandlerRequestDTO>()

            meldingService.createMeldingTilBehandler(
                requestDTO = requestDTO,
                callId = getCallId(),
                veilederIdent = veilederIdent,
                personIdent = personIdent,
            )

            call.respond(HttpStatusCode.OK)
        }

        post("/{$uuid}/paminnelse") {
            val personIdent = call.personIdent()
            val veilederIdent = call.getNAVIdent()
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                personIdent = personIdent
            )

            val meldingUuid = call.meldingUuid()
            val requestDTO = call.receive<PaminnelseRequestDTO>()

            meldingService.createPaminnelse(
                callId = getCallId(),
                meldingUuid = meldingUuid,
                veilederIdent = veilederIdent,
                document = requestDTO.document,
            )

            call.respond(HttpStatusCode.OK)
        }

        post("/{$uuid}/retur") {
            val personIdent = call.personIdent()
            val veilederIdent = call.getNAVIdent()
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                personIdent = personIdent
            )

            val meldingUuid = call.meldingUuid()
            val requestDTO = call.receive<ReturAvLegeerklaringRequestDTO>()

            meldingService.createReturAvLegeerklaring(
                callId = getCallId(),
                meldingUuid = meldingUuid,
                veilederIdent = veilederIdent,
                document = requestDTO.document,
                tekst = requestDTO.tekst,
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun ApplicationCall.meldingUuid(): UUID = UUID.fromString(this.parameters[uuid])
    ?: throw IllegalArgumentException("Missing value for melding uuid")

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
