package no.nav.syfo.melding.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.util.getBearerHeader

const val meldingerSystemApiBasePath = "/api/system/v1/meldinger"
const val msgIdParam = "msgId"
private val authorizedApps = listOf("padm2")

fun Route.registerMeldingSystemApi(
    apiConsumerAccessService: APIConsumerAccessService,
    meldingService: MeldingService,
) {
    route(meldingerSystemApiBasePath) {
        get("/{$msgIdParam}") {
            val token = this.call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to retrieve melding: No token supplied in request header")
            apiConsumerAccessService.validateConsumerApplicationAZP(
                token = token,
                authorizedApps = authorizedApps,
            )

            val msgId = this.call.parameters[msgIdParam] ?: throw IllegalArgumentException("Missing msgId")
            if (meldingService.hasMelding(msgId)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
