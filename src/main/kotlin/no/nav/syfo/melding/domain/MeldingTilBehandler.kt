package no.nav.syfo.melding.domain

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.Melding
import no.nav.syfo.melding.kafka.domain.*
import java.time.OffsetDateTime
import java.util.*

data class MeldingTilBehandler(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val type: MeldingType,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val bestiltTidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: PersonIdent,
    val behandlerPersonIdent: PersonIdent?,
    val behandlerNavn: String?,
    val behandlerRef: UUID,
    val tekst: String,
    val document: List<DocumentComponentDTO>,
    val antallVedlegg: Int,
)

fun MeldingTilBehandler.toPMelding() = PMelding(
    uuid = uuid,
    createdAt = createdAt,
    innkommende = false,
    type = type.name,
    conversationRef = conversationRef,
    parentRef = parentRef,
    msgId = null,
    tidspunkt = bestiltTidspunkt,
    arbeidstakerPersonIdent = arbeidstakerPersonIdent.value,
    behandlerPersonIdent = behandlerPersonIdent?.value,
    behandlerNavn = behandlerNavn,
    behandlerRef = behandlerRef,
    tekst = tekst,
    document = document,
    antallVedlegg = antallVedlegg,
    innkommendePublishedAt = null,
    journalpostId = null,
)

fun MeldingTilBehandler.toMelding() = Melding(
    uuid = uuid,
    behandlerRef = behandlerRef,
    behandlerNavn = null,
    tekst = tekst,
    document = document,
    tidspunkt = bestiltTidspunkt,
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
