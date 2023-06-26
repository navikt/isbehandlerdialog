package no.nav.syfo.client.pdfgen

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.melding.domain.DocumentComponentDTO
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory

class PdfGenClient(
    pdfGenBaseUrl: String,
    private val httpClient: HttpClient = httpClientDefault()
) {
    private val foresporselOmPasientUrl: String
    private val foresporselOmPasientPaminnelseUrl: String

    init {
        this.foresporselOmPasientUrl = "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_PATH"
        this.foresporselOmPasientPaminnelseUrl = "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_PAMINNELSE_PATH"
    }

    suspend fun generateForesporselOmPasient(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = foresporselOmPasientUrl,
        )
    }

    suspend fun generateForesporselOmPasientPaminnelse(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? {
        return getPdf(
            callId = callId,
            documentComponentDTOList = documentComponentDTOList,
            pdfUrl = foresporselOmPasientPaminnelseUrl,
        )
    }

    private suspend fun getPdf(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        pdfUrl: String,
    ): ByteArray? {
        return try {
            val response: HttpResponse = httpClient.post(pdfUrl) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(documentComponentDTOList)
            }
            COUNT_CALL_PDFGEN_SUCCESS.increment()
            response.body()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(pdfUrl, e.response, callId)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(pdfUrl, e.response, callId)
        }
    }

    private fun handleUnexpectedResponseException(
        url: String,
        response: HttpResponse,
        callId: String,
    ): ByteArray? {
        log.error(
            "Error while requesting PDF from dialogmeldingpdfgen with {}, {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("url", url),
            callIdArgument(callId)
        )
        COUNT_CALL_PDFGEN_FAIL.increment()
        return null
    }

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isbehandlerdialog"
        const val FORESPORSEL_OM_PASIENT_PATH = "$API_BASE_PATH/foresporselompasient" // TODO: Spesifiser her også?
        const val FORESPORSEL_OM_PASIENT_PAMINNELSE_PATH = "$API_BASE_PATH/foresporselompasient-paminnelse"

        private val log = LoggerFactory.getLogger(PdfGenClient::class.java)
    }
}
