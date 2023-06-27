package no.nav.syfo.melding.domain

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingDTO
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.toMeldingStatusDTO
import java.time.OffsetDateTime
import java.util.*

data class MeldingTilBehandler(
    override val uuid: UUID,
    val createdAt: OffsetDateTime,
    override val type: MeldingType,
    override val conversationRef: UUID,
    override val parentRef: UUID?,
    override val tidspunkt: OffsetDateTime,
    override val arbeidstakerPersonIdent: PersonIdent,
    override val behandlerPersonIdent: PersonIdent?,
    override val behandlerNavn: String?,
    override val behandlerRef: UUID,
    override val tekst: String,
    override val document: List<DocumentComponentDTO>,
    override val antallVedlegg: Int,
    val ubesvartPublishedAt: OffsetDateTime?,
    override val veilederIdent: String?,
) : Melding {
    override val msgId: String? = null
    override val innkommende: Boolean = false
    override val journalpostId: String? = null
}

fun MeldingTilBehandler.toMeldingDTO(status: MeldingStatus?) = MeldingDTO(
    uuid = uuid,
    behandlerRef = behandlerRef,
    behandlerNavn = null,
    tekst = tekst,
    document = document,
    tidspunkt = tidspunkt,
    innkommende = false,
    type = type,
    antallVedlegg = antallVedlegg,
    status = status?.toMeldingStatusDTO(),
    veilederIdent = veilederIdent,
)

fun MeldingTilBehandler.toDialogmeldingBestillingDTO(meldingPdf: ByteArray) = DialogmeldingBestillingDTO(
    behandlerRef = this.behandlerRef.toString(),
    personIdent = this.arbeidstakerPersonIdent.value,
    dialogmeldingUuid = this.uuid.toString(),
    dialogmeldingRefParent = this.parentRef?.toString(),
    dialogmeldingRefConversation = this.conversationRef.toString(),
    dialogmeldingType = this.getDialogmeldingType().name,
    dialogmeldingKodeverk = this.getDialogmeldingKodeverk().name,
    dialogmeldingKode = this.getDialogmeldingKode().value,
    dialogmeldingTekst = this.document.serialize(),
    dialogmeldingVedlegg = meldingPdf,
)

private fun MeldingTilBehandler.getDialogmeldingKode(): DialogmeldingKode {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER -> DialogmeldingKode.FORESPORSEL
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> DialogmeldingKode.PAMINNELSE_FORESPORSEL
    }
}

private fun MeldingTilBehandler.getDialogmeldingKodeverk(): DialogmeldingKodeverk {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> DialogmeldingKodeverk.FORESPORSEL
    }
}

private fun MeldingTilBehandler.getDialogmeldingType(): DialogmeldingType {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> DialogmeldingType.DIALOG_FORESPORSEL
    }
}

private fun MeldingTilBehandler.getBrevKode(): BrevkodeType {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER -> BrevkodeType.FORESPORSEL_OM_PASIENT
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> BrevkodeType.FORESPORSEL_OM_PASIENT_PAMINNELSE
    }
}

fun MeldingTilBehandler.toJournalpostRequest(pdf: ByteArray) =
    JournalpostRequest(
        avsenderMottaker = createAvsenderMottaker(behandlerPersonIdent, behandlerNavn),
        tittel = this.createTittel(),
        bruker = Bruker.create(
            id = arbeidstakerPersonIdent.value,
            idType = BrukerIdType.PERSON_IDENT,
        ),
        dokumenter = listOf(
            Dokument.create(
                brevkode = this.getBrevKode(),
                tittel = this.createTittel(),
                dokumentvarianter = listOf(
                    Dokumentvariant.create(
                        filnavn = this.createTittel(),
                        filtype = FiltypeType.PDFA,
                        fysiskDokument = pdf,
                        variantformat = VariantformatType.ARKIV,
                    )
                ),
            )
        ),
        overstyrInnsynsregler = this.createOverstyrInnsynsregler(),
    )

fun MeldingTilBehandler.createTittel(): String {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER -> MeldingTittel.DIALOGMELDING_DEFAULT.value
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> MeldingTittel.DIALOGMELDING_PAMINNELSE.value
    }
}

fun MeldingTilBehandler.createOverstyrInnsynsregler(): String? {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER -> OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.value
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> null
    }
}

fun MeldingTilBehandler.toKafkaMeldingDTO() = KafkaMeldingDTO(
    uuid = uuid.toString(),
    personIdent = arbeidstakerPersonIdent.value,
    type = type.name,
    conversationRef = conversationRef.toString(),
    parentRef = parentRef?.toString(),
    msgId = msgId,
    tidspunkt = tidspunkt,
    behandlerPersonIdent = behandlerPersonIdent?.value,
)

fun createAvsenderMottaker(
    behandlerPersonIdent: PersonIdent?,
    behandlerNavn: String?,
): AvsenderMottaker {
    return if (behandlerPersonIdent == null) {
        AvsenderMottaker.create(
            id = null,
            idType = null,
            navn = behandlerNavn,
        )
    } else {
        AvsenderMottaker.create(
            id = behandlerPersonIdent.value,
            idType = BrukerIdType.PERSON_IDENT,
            navn = behandlerNavn,
        )
    }
}
