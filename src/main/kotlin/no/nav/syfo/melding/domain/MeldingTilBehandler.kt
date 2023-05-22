package no.nav.syfo.melding.domain

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingDTO
import no.nav.syfo.melding.kafka.domain.*
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
) : Melding {
    override val msgId: String? = null
    override val innkommende: Boolean = false
    override val journalpostId: String? = null
}

fun MeldingTilBehandler.toMelding() = MeldingDTO(
    uuid = uuid,
    behandlerRef = behandlerRef,
    behandlerNavn = null,
    tekst = tekst,
    document = document,
    tidspunkt = tidspunkt,
    innkommende = false,
    antallVedlegg = antallVedlegg,
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
        MeldingType.FORESPORSEL_PASIENT -> DialogmeldingKode.FORESPORSEL
    }
}

private fun MeldingTilBehandler.getDialogmeldingKodeverk(): DialogmeldingKodeverk {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT -> DialogmeldingKodeverk.FORESPORSEL
    }
}

private fun MeldingTilBehandler.getDialogmeldingType(): DialogmeldingType {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT -> DialogmeldingType.DIALOG_FORESPORSEL
    }
}

fun MeldingTilBehandler.toJournalpostRequest(pdf: ByteArray) = JournalpostRequest(
    avsenderMottaker = createAvsenderMottaker(behandlerPersonIdent, behandlerNavn),
    tittel = "Dialogmelding til behandler",
    bruker = Bruker.create(
        id = arbeidstakerPersonIdent.value,
        idType = BrukerIdType.PERSON_IDENT,
    ),
    dokumenter = listOf(
        Dokument.create(
            brevkode = BrevkodeType.FORESPORSEL_OM_PASIENT,
            tittel = "Dialogmelding til behandler",
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = "Dialogmelding til behandler",
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
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
