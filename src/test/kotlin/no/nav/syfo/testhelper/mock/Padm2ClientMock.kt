package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.client.padm2.VedleggDTO
import no.nav.syfo.testhelper.UserConstants.MSG_ID_WITH_VEDLEGG
import no.nav.syfo.testhelper.UserConstants.VEDLEGG_BYTEARRAY
import java.util.UUID

const val vedleggSystemApiMsgIdParam = "msgid"
class Padm2ClientMock : MockServer() {
    override val name = "pamd2client"

    override val routingConfiguration: Routing.() -> Unit = {
        get("${Padm2Client.Companion.HENT_VEDLEGG_PATH}/{$vedleggSystemApiMsgIdParam}") {
            val msgId = UUID.fromString(call.parameters[vedleggSystemApiMsgIdParam])
            call.respond(
                if (msgId == MSG_ID_WITH_VEDLEGG) {
                    listOf(VedleggDTO(VEDLEGG_BYTEARRAY))
                } else {
                    emptyList()
                }
            )
        }
    }
}
