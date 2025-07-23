package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.padm2.VedleggDTO
import no.nav.syfo.testhelper.UserConstants.MSG_ID_WITH_VEDLEGG
import no.nav.syfo.testhelper.UserConstants.VEDLEGG_BYTEARRAY
import java.util.*

fun MockRequestHandleScope.padm2ClientMockResponse(request: HttpRequestData): HttpResponseData {
    val msgIdParam = request.url.encodedPath.split("/").last()
    return when (UUID.fromString(msgIdParam)) {
        MSG_ID_WITH_VEDLEGG -> respond(listOf(VedleggDTO(VEDLEGG_BYTEARRAY)))
        else -> respond(emptyList<VedleggDTO>())
    }
}
