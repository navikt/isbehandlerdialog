package no.nav.syfo.behandlerdialog.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.VeilederTilgangskontrollPlugin
import no.nav.syfo.behandlerdialog.api.domain.BehandlerdialogDTO
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*

const val behandlerdialogApiBasePath = "/api/internad/v1/behandlerdialog"
const val behandlerdialogApiPersonidentPath = "/personident" // TODO: Noe annet enn /personident eller?

private const val API_ACTION = "access behandlerdialog for person"

fun Route.registerBehandlerdialogApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(behandlerdialogApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }

        get(behandlerdialogApiPersonidentPath) {
            // Hent ut fra databasen her
            call.respond(HttpStatusCode.OK)
        }

        post(behandlerdialogApiPersonidentPath) {
            val personIdent = call.personIdent()
            val requestDTO = call.receive<BehandlerdialogDTO>()
            // Lagre i db, legg til i joark-cronjob og send på kafka her
            call.respond(requestDTO)
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
