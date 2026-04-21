package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.veiledertilgang.Tilgang
import no.nav.syfo.testhelper.UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_NO_WRITE_ACCESS
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.getNavIdentFromToken

private fun HttpRequestData.navIdent(): String? =
    headers[HttpHeaders.Authorization]
        ?.removePrefix("Bearer ")
        ?.let { getNavIdentFromToken(it) }

fun MockRequestHandleScope.tilgangskontrollMockResponse(request: HttpRequestData): HttpResponseData {
    val erGodkjent = request.headers[NAV_PERSONIDENT_HEADER] != PERSONIDENT_VEILEDER_NO_ACCESS.value
    val fullTilgang = request.navIdent() != VEILEDER_IDENT_NO_WRITE_ACCESS
    return respond(Tilgang(erGodkjent = erGodkjent, fullTilgang = fullTilgang))
}
