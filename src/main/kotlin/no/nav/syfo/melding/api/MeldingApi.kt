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

const val meldingApiBasePath = "/api/internad/v1/melding"

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

        post {
            val personIdent = call.personIdent()
            val requestDTO = call.receive<MeldingTilBehandlerRequestDTO>()

            meldingService.createMeldingTilBehandler(
                meldingTilBehandler = requestDTO.toMeldingTilBehandler(personIdent),
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
