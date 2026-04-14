package no.nav.syfo.infrastructure.client.pdfgen

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.IPdfGenClient
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.Melding
import no.nav.syfo.infrastructure.client.httpClientDefault
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient.Companion.illegalCharsRegex
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient.Companion.log
import no.nav.syfo.infrastructure.kafka.domain.Status
import no.nav.syfo.infrastructure.kafka.domain.ValidationResult
import no.nav.syfo.infrastructure.kafka.legeerklaring.Legeerklaering
import no.nav.syfo.infrastructure.kafka.legeerklaring.LegeerklaringDTO
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PdfGenClient(
    private val pdfGenBaseUrl: String,
    private val legeerklaringPdfGenBaseUrl: String,
    private val httpClient: HttpClient = httpClientDefault(),
) : IPdfGenClient {

    override suspend fun generateDialogPdf(
        callId: String,
        mottakerNavn: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        meldingType: Melding.MeldingType,
    ): ByteArray? {
        val pdfUrl =
            when (meldingType) {
                Melding.MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER -> FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER_URL
                Melding.MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> FORESPORSEL_OM_PASIENT_PAMINNELSE_URL
                Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> FORESPORSEL_OM_PASIENT_LEGEERKLARING_URL
                Melding.MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> RETUR_LEGEERKLARING_URL
                Melding.MeldingType.HENVENDELSE_MELDING_FRA_NAV -> HENVENDELSE_MELDING_FRA_NAV_URL
                Melding.MeldingType.HENVENDELSE_MELDING_TIL_NAV -> throw RuntimeException("Should only be used for incoming messages")
            }
        return getPdf(
            callId = callId,
            payload = PdfModel.create(mottakerNavn = mottakerNavn, documentComponents = documentComponentDTOList),
            pdfUrl = pdfGenBaseUrl + pdfUrl,
        )
    }

    override suspend fun generateLegeerklaring(legeerklaringDTO: LegeerklaringDTO): ByteArray? =
        getPdf(
            callId = UUID.randomUUID().toString(),
            payload = PdfModelLegeerklaring(
                legeerklaering = legeerklaringDTO.legeerklaering.sanitizeForPdfGen(),
                validationResult = ValidationResult(Status.OK),
                mottattDato = legeerklaringDTO.mottattDato,
            ),
            pdfUrl = legeerklaringPdfGenBaseUrl + LEGEERKLARING_URL,
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

    private data class PdfModel(
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

    private data class PdfModelLegeerklaring(
        val legeerklaering: Legeerklaering,
        val validationResult: ValidationResult,
        val mottattDato: LocalDateTime,
    )

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isbehandlerdialog"

        private const val FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER_URL = "$API_BASE_PATH/foresporselompasient-tilleggsopplysninger"
        private const val FORESPORSEL_OM_PASIENT_LEGEERKLARING_URL = "$API_BASE_PATH/foresporselompasient-legeerklaring"
        private const val FORESPORSEL_OM_PASIENT_PAMINNELSE_URL = "$API_BASE_PATH/foresporselompasient-paminnelse"
        private const val RETUR_LEGEERKLARING_URL = "$API_BASE_PATH/henvendelse-retur-legeerklaring"
        private const val HENVENDELSE_MELDING_FRA_NAV_URL = "$API_BASE_PATH/henvendelse-meldingfranav"
        private const val LEGEERKLARING_URL = "/api/v1/genpdf/pale-2/pale-2"

        val log: Logger = LoggerFactory.getLogger(PdfGenClient::class.java)
        val illegalCharsRegex = Regex("""[^\t\r\n\x20-\x7E\x80-\xFF]""")
    }
}

fun List<DocumentComponentDTO>.sanitizeForPdfGen(): List<DocumentComponentDTO> = this.map {
    it.copy(
        texts = it.texts.map { text -> text.sanitizeForPdfGen() }
    )
}

private fun String.sanitizeForPdfGen(): String {
    val sanitized = this.replace(illegalCharsRegex) { match ->
        log.warn("Illegal character in document: U+%04X".format(match.value.first().code))
        ""
    }
    return sanitized
        .replace("\u00A0", " ")
        .replace("\u00AD", "")
}

private fun Legeerklaering.sanitizeForPdfGen(): Legeerklaering = this.copy(
    pasient = pasient.copy(
        fornavn = pasient.fornavn.sanitizeForPdfGen(),
        mellomnavn = pasient.mellomnavn?.sanitizeForPdfGen(),
        etternavn = pasient.etternavn.sanitizeForPdfGen(),
        navKontor = pasient.navKontor?.sanitizeForPdfGen(),
        adresse = pasient.adresse?.sanitizeForPdfGen(),
        poststed = pasient.poststed?.sanitizeForPdfGen(),
        yrke = pasient.yrke?.sanitizeForPdfGen(),
        arbeidsgiver = pasient.arbeidsgiver.copy(
            navn = pasient.arbeidsgiver.navn?.sanitizeForPdfGen(),
            adresse = pasient.arbeidsgiver.adresse?.sanitizeForPdfGen(),
            poststed = pasient.arbeidsgiver.poststed?.sanitizeForPdfGen(),
        ),
    ),
    sykdomsopplysninger = sykdomsopplysninger.copy(
        sykdomshistorie = sykdomsopplysninger.sykdomshistorie.sanitizeForPdfGen(),
        statusPresens = sykdomsopplysninger.statusPresens.sanitizeForPdfGen(),
        hoveddiagnose = sykdomsopplysninger.hoveddiagnose?.copy(
            tekst = sykdomsopplysninger.hoveddiagnose.tekst?.sanitizeForPdfGen(),
        ),
        bidiagnose = sykdomsopplysninger.bidiagnose.map { diagnose ->
            diagnose?.copy(tekst = diagnose.tekst?.sanitizeForPdfGen())
        },
    ),
    plan = plan?.copy(
        utredningsplan = plan.utredningsplan?.sanitizeForPdfGen(),
        behandlingsplan = plan.behandlingsplan?.sanitizeForPdfGen(),
        vurderingAvTidligerePlan = plan.vurderingAvTidligerePlan?.sanitizeForPdfGen(),
        narSporreOmNyeLegeopplysninger = plan.narSporreOmNyeLegeopplysninger?.sanitizeForPdfGen(),
        videreBehandlingIkkeAktueltGrunn = plan.videreBehandlingIkkeAktueltGrunn?.sanitizeForPdfGen(),
        utredning = plan.utredning?.copy(tekst = plan.utredning.tekst.sanitizeForPdfGen()),
        behandling = plan.behandling?.copy(tekst = plan.behandling.tekst.sanitizeForPdfGen()),
    ),
    forslagTilTiltak = forslagTilTiltak.copy(
        andreTiltak = forslagTilTiltak.andreTiltak?.sanitizeForPdfGen(),
        naermereOpplysninger = forslagTilTiltak.naermereOpplysninger.sanitizeForPdfGen(),
        tekst = forslagTilTiltak.tekst.sanitizeForPdfGen(),
    ),
    funksjonsOgArbeidsevne = funksjonsOgArbeidsevne.copy(
        vurderingFunksjonsevne = funksjonsOgArbeidsevne.vurderingFunksjonsevne?.sanitizeForPdfGen(),
        annetArbeid = funksjonsOgArbeidsevne.annetArbeid.sanitizeForPdfGen(),
        kravTilArbeid = funksjonsOgArbeidsevne.kravTilArbeid?.sanitizeForPdfGen(),
        kanIkkeGjenopptaNaverendeArbeid = funksjonsOgArbeidsevne.kanIkkeGjenopptaNaverendeArbeid?.sanitizeForPdfGen(),
        kanIkkeTaAnnetArbeid = funksjonsOgArbeidsevne.kanIkkeTaAnnetArbeid?.sanitizeForPdfGen(),
    ),
    prognose = prognose.copy(
        anslattVarighetSykdom = prognose.anslattVarighetSykdom?.sanitizeForPdfGen(),
        anslattVarighetFunksjonsnedsetting = prognose.anslattVarighetFunksjonsnedsetting?.sanitizeForPdfGen(),
        anslattVarighetNedsattArbeidsevne = prognose.anslattVarighetNedsattArbeidsevne?.sanitizeForPdfGen(),
    ),
    arsakssammenheng = arsakssammenheng?.sanitizeForPdfGen(),
    andreOpplysninger = andreOpplysninger?.sanitizeForPdfGen(),
    pasientenBurdeIkkeVite = pasientenBurdeIkkeVite?.sanitizeForPdfGen(),
    kontakt = kontakt.copy(
        kontakteAnnenInstans = kontakt.kontakteAnnenInstans?.sanitizeForPdfGen(),
    ),
    signatur = signatur.copy(
        navn = signatur.navn?.sanitizeForPdfGen(),
        adresse = signatur.adresse?.sanitizeForPdfGen(),
        poststed = signatur.poststed?.sanitizeForPdfGen(),
    ),
)
