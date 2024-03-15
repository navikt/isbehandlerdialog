package no.nav.syfo.client.pdfgen

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.illegalCharacters
import no.nav.syfo.client.pdfgen.PdfGenClient.Companion.log
import no.nav.syfo.melding.domain.DocumentComponentDTO
import no.nav.syfo.melding.kafka.domain.Status
import no.nav.syfo.melding.kafka.domain.ValidationResult
import no.nav.syfo.melding.kafka.legeerklaring.Legeerklaering
import no.nav.syfo.melding.kafka.legeerklaring.LegeerklaringDTO
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PdfGenClient(
    pdfGenBaseUrl: String,
    legeerklaringPdfGenBaseUrl: String,
    private val httpClient: HttpClient = httpClientDefault()
) {
    private val foresporselOmPasientTilleggsopplysningerUrl: String
    private val foresporselOmPasientLegeerklaringUrl: String
    private val foresporselOmPasientPaminnelseUrl: String
    private val legeerklaringPdfUrl: String
    private val returLegeerklaringPdfUrl: String
    private val henvendelseMeldingFraNavPdfUrl: String

    init {
        this.foresporselOmPasientTilleggsopplysningerUrl =
            "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER_PATH"
        this.foresporselOmPasientLegeerklaringUrl = "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_LEGEERKLARING_PATH"
        this.foresporselOmPasientPaminnelseUrl = "$pdfGenBaseUrl$FORESPORSEL_OM_PASIENT_PAMINNELSE_PATH"
        this.legeerklaringPdfUrl = "$legeerklaringPdfGenBaseUrl$LEGEERKLARING_PATH"
        this.returLegeerklaringPdfUrl = "$pdfGenBaseUrl$RETUR_LEGEERKLARING_PATH"
        this.henvendelseMeldingFraNavPdfUrl = "$pdfGenBaseUrl$HENVENDELSE_MELDING_FRA_NAV_PATH"
    }

    suspend fun generateForesporselOmPasientTilleggsopplysinger(
        callId: String,
        mottakerNavn: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? = getPdf(
        callId = callId,
        payload = PdfModel.create(
            mottakerNavn = mottakerNavn,
            documentComponents = documentComponentDTOList,
        ),
        pdfUrl = foresporselOmPasientTilleggsopplysningerUrl,
    )

    suspend fun generateForesporselOmPasientPaminnelse(
        callId: String,
        mottakerNavn: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? = getPdf(
        callId = callId,
        payload = PdfModel.create(
            mottakerNavn = mottakerNavn,
            documentComponents = documentComponentDTOList,
        ),
        pdfUrl = foresporselOmPasientPaminnelseUrl,
    )

    suspend fun generateForesporselOmPasientLegeerklaring(
        callId: String,
        mottakerNavn: String,
        documentComponentDTOList: List<DocumentComponentDTO>
    ): ByteArray? = getPdf(
        callId = callId,
        payload = PdfModel.create(
            mottakerNavn = mottakerNavn,
            documentComponents = documentComponentDTOList,
        ),
        pdfUrl = foresporselOmPasientLegeerklaringUrl,
    )

    suspend fun generateReturAvLegeerklaring(
        callId: String,
        mottakerNavn: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? = getPdf(
        callId = callId,
        payload = PdfModel.create(
            mottakerNavn = mottakerNavn,
            documentComponents = documentComponentDTOList,
        ),
        pdfUrl = returLegeerklaringPdfUrl,
    )

    suspend fun generateLegeerklaring(
        legeerklaringDTO: LegeerklaringDTO,
    ): ByteArray? = getPdf(
        callId = UUID.randomUUID().toString(),
        payload = PdfModelLegeerklaring(
            legeerklaering = legeerklaringDTO.legeerklaering,
            validationResult = ValidationResult(Status.OK),
            mottattDato = legeerklaringDTO.mottattDato,
        ),
        pdfUrl = legeerklaringPdfUrl,
    )

    suspend fun generateMeldingFraNav(
        callId: String,
        mottakerNavn: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
    ): ByteArray? = getPdf(
        callId = callId,
        payload = PdfModel.create(
            mottakerNavn = mottakerNavn,
            documentComponents = documentComponentDTOList,
        ),
        pdfUrl = henvendelseMeldingFraNavPdfUrl,
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
        val mottakerNavn: String,
        val datoSendt: String,
        val documentComponents: List<DocumentComponentDTO>,
    ) {
        companion object {
            private val formatter = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale("no", "NO"))

            fun create(
                mottakerNavn: String,
                documentComponents: List<DocumentComponentDTO>,
            ) = PdfModel(
                mottakerNavn = mottakerNavn,
                datoSendt = LocalDate.now().format(formatter),
                documentComponents = documentComponents.sanitizeForPdfGen(),
            )
        }
    }

    data class PdfModelLegeerklaring(
        val legeerklaering: Legeerklaering,
        val validationResult: ValidationResult,
        val mottattDato: LocalDateTime,
    )

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isbehandlerdialog"
        const val FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER_PATH =
            "$API_BASE_PATH/foresporselompasient-tilleggsopplysninger-v2"
        const val FORESPORSEL_OM_PASIENT_LEGEERKLARING_PATH = "$API_BASE_PATH/foresporselompasient-legeerklaring-v2"
        const val FORESPORSEL_OM_PASIENT_PAMINNELSE_PATH = "$API_BASE_PATH/foresporselompasient-paminnelse-v2"
        const val LEGEERKLARING_PATH = "/api/v1/genpdf/pale-2/pale-2"
        const val RETUR_LEGEERKLARING_PATH = "$API_BASE_PATH/henvendelse-retur-legeerklaring-v2"
        const val HENVENDELSE_MELDING_FRA_NAV_PATH = "$API_BASE_PATH/henvendelse-meldingfranav-v2"

        val log: Logger = LoggerFactory.getLogger(PdfGenClient::class.java)
        val illegalCharacters = listOf('\u0002')
    }
}

fun List<DocumentComponentDTO>.sanitizeForPdfGen(): List<DocumentComponentDTO> = this.map {
    it.copy(
        texts = it.texts.map { text ->
            text.toCharArray().filter { char ->
                if (char in illegalCharacters) {
                    log.warn("Illegal character in document: %x".format(char.code))
                    false
                } else {
                    true
                }
            }.joinToString("")
        }
    )
}
