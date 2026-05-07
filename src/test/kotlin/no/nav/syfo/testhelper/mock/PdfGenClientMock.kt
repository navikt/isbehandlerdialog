package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.pdfGenClientMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    val apiBasePath = "/api/v1/genpdf/isbehandlerdialog"
    return when {
        requestUrl.endsWith("$apiBasePath/foresporselompasient-tilleggsopplysninger") -> {
            respond(content = UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER)
        }
        requestUrl.endsWith("$apiBasePath/foresporselompasient-legeerklaring") -> {
            respond(content = UserConstants.PDF_FORESPORSEL_OM_PASIENT_LEGEERKLARING)
        }
        requestUrl.endsWith("$apiBasePath/foresporselompasient-paminnelse") -> {
            respond(content = UserConstants.PDF_FORESPORSEL_OM_PASIENT_PAMINNELSE)
        }
        requestUrl.endsWith("$apiBasePath/legeerklaring") -> {
            val bodyString = String(request.body.toByteArray(), Charsets.UTF_8)
            if (Regex("""[^\t\r\n\x20-\x7E\x80-\xFF]""").containsMatchIn(bodyString)) {
                respond(content = "Bad request - illegal characters in body".toByteArray(), status = HttpStatusCode.BadRequest)
            } else {
                respond(content = UserConstants.PDF_LEGEERKLARING)
            }
        }
        requestUrl.endsWith("$apiBasePath/henvendelse-retur-legeerklaring") -> {
            respond(content = UserConstants.PDF_RETUR_LEGEERKLARING)
        }
        requestUrl.endsWith("$apiBasePath/henvendelse-meldingfranav") -> {
            respond(content = UserConstants.PDF_HENVENDELSE_MELDING_FRA_NAV)
        }

        else -> error("Unhandled pdf ${request.url.encodedPath}")
    }
}
