package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.testhelper.UserConstants

fun MockRequestHandleScope.pdfGenClientMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    return when {
        requestUrl.endsWith(PdfGenClient.Companion.FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER_PATH) -> {
            respond(content = UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER)
        }
        requestUrl.endsWith(PdfGenClient.Companion.FORESPORSEL_OM_PASIENT_LEGEERKLARING_PATH) -> {
            respond(content = UserConstants.PDF_FORESPORSEL_OM_PASIENT_LEGEERKLARING)
        }
        requestUrl.endsWith(PdfGenClient.Companion.FORESPORSEL_OM_PASIENT_PAMINNELSE_PATH) -> {
            respond(content = UserConstants.PDF_FORESPORSEL_OM_PASIENT_PAMINNELSE)
        }

        else -> error("Unhandled pdf ${request.url.encodedPath}")
    }
}
