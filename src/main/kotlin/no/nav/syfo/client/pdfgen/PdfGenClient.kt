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
import no.nav.syfo.melding.kafka.domain.Status
import no.nav.syfo.melding.kafka.domain.ValidationResult
import no.nav.syfo.melding.kafka.legeerklaring.Legeerklaering
import no.nav.syfo.melding.kafka.legeerklaring.LegeerklaringDTO
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class PdfGenClient(
    pdfGenBaseUrl: String,
    legeerklaringPdfGenBaseUrl: String,
    private val httpClient: HttpClient = httpClientDefault()
) {
    private val foresporselOmPasientTilleggsopplysningerUrl: String
    private val foresporselOmPasientLegeerklaringUrl: String
    private val foresporselOmPasientPaminnelseUrl: String
    private val legeerklaringPdfUrl: String

    init {
        this.foresporselOmPasientTilleggsopplysningerUrl =
            "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER_PATH"
        this.foresporselOmPasientLegeerklaringUrl = "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_LEGEERKLARING_PATH"
        this.foresporselOmPasientPaminnelseUrl = "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_PAMINNELSE_PATH"
        this.legeerklaringPdfUrl = "$legeerklaringPdfGenBaseUrl$LEGEERKLARING_PATH"
    }

    suspend fun generateForesporselOmPasientTilleggsopplysinger(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? = getPdf(
        callId = callId,
        payload = documentComponentDTOList,
        pdfUrl = foresporselOmPasientTilleggsopplysningerUrl,
    )

    suspend fun generateForesporselOmPasientPaminnelse(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? = getPdf(
        callId = callId,
        payload = documentComponentDTOList,
        pdfUrl = foresporselOmPasientPaminnelseUrl,
    )

    suspend fun generateForesporselOmPasientLegeerklaring(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>
    ): ByteArray? = getPdf(
        callId = callId,
        payload = documentComponentDTOList,
        pdfUrl = foresporselOmPasientLegeerklaringUrl,
    )

    suspend fun generateReturAvLegeerklaring(
        callId: String,
        documentComponentDTOList: List<DocumentComponentDTO>
    ): ByteArray? {
        // TODO: Implement
        return byteArrayOf(0x2E, 0x28)
    }

    suspend fun generateLegeerklaring(
        legeerklaringDTO: LegeerklaringDTO,
    ): ByteArray? = getPdf(
        callId = UUID.randomUUID().toString(),
        payload = PdfModel(
            legeerklaering = legeerklaringDTO.legeerklaering,
            validationResult = ValidationResult(Status.OK),
            mottattDato = legeerklaringDTO.mottattDato,
        ),
        pdfUrl = legeerklaringPdfUrl,
    )

    private suspend fun getPdf(
        callId: String,
        payload: Any,
        pdfUrl: String,
    ): ByteArray? {
        return try {
            val response: HttpResponse = httpClient.post(pdfUrl) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(payload)
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

    data class PdfModel(
        val legeerklaering: Legeerklaering,
        val validationResult: ValidationResult,
        val mottattDato: LocalDateTime,
    )

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isbehandlerdialog"
        const val FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER_PATH =
            "$API_BASE_PATH/foresporselompasient-tilleggsopplysninger"
        const val FORESPORSEL_OM_PASIENT_LEGEERKLARING_PATH = "$API_BASE_PATH/foresporselompasient-legeerklaring"
        const val FORESPORSEL_OM_PASIENT_PAMINNELSE_PATH = "$API_BASE_PATH/foresporselompasient-paminnelse"
        const val LEGEERKLARING_PATH = "/api/v1/genpdf/pale-2/pale-2"

        private val log = LoggerFactory.getLogger(PdfGenClient::class.java)
    }
}
