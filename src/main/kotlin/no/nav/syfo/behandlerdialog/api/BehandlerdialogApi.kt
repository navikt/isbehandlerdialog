package no.nav.syfo.behandlerdialog.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.VeilederTilgangskontrollPlugin
import no.nav.syfo.behandlerdialog.MeldingTilBehandlerService
import no.nav.syfo.behandlerdialog.domain.toMeldingTilBehandlerResponseDTO
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*

const val behandlerdialogApiBasePath = "/api/internad/v1/behandlerdialog"
const val behandlerdialogApiMeldingPath = "/melding"

private const val API_ACTION = "access behandlerdialog for person"

fun Route.registerBehandlerdialogApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    meldingTilBehandlerService: MeldingTilBehandlerService,
) {
    route(behandlerdialogApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }

        get {
            val personIdent = call.personIdent()
            val responseDTOList = meldingTilBehandlerService
                .getMeldingerTilBehandler(personIdent)
                .map { it.toMeldingTilBehandlerResponseDTO() }
            call.respond(responseDTOList)
        }

        post(behandlerdialogApiMeldingPath) {
            val personIdent = call.personIdent()
            val requestDTO = call.receive<MeldingTilBehandlerRequestDTO>()

            meldingTilBehandlerService.createMeldingTilBehandler(
                meldingTilBehandler = requestDTO.toMeldingTilBehandler(personIdent),
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
