package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.testhelper.UserConstants

class PdfGenClientMock : MockServer() {
    override val name = "pdfgenclient"

    override val routingConfiguration: Routing.() -> Unit = {
        post(PdfGenClient.Companion.FORESPORSEL_OM_PASIENT_PATH) {
            call.respond(UserConstants.PDF_FORESPORSEL_OM_PASIENT)
        }
    }
}
