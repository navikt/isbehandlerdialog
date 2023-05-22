package no.nav.syfo.application.api

import io.ktor.server.application.*
import no.nav.syfo.application.exception.ForbiddenAccessVeilederException
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*

suspend fun ApplicationCall.checkVeilederTilgang(
    action: String,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    personIdent: PersonIdent,
) {
    val callId = getCallId()
    val token = getBearerHeader()
        ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")

    val hasAccess = veilederTilgangskontrollClient.hasAccess(
        callId = callId,
        personIdent = personIdent,
        token = token,
    )
    if (!hasAccess) {
        throw ForbiddenAccessVeilederException(
            action = action,
        )
    }
}
