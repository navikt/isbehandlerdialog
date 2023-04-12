package no.nav.syfo.melding.domain

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
    behandlerPersonIdent = null,
    behandlerNavn = null,
    behandlerRef = behandlerRef,
    tekst = tekst,
    document = document,
    antallVedlegg = antallVedlegg,
)

fun MeldingTilBehandler.toMelding() = Melding(
    behandlerRef = behandlerRef,
    behandlerNavn = null,
    tekst = tekst,
    document = document,
    tidspunkt = bestiltTidspunkt,
    innkommende = false,
)

fun MeldingTilBehandler.toDialogmeldingBestillingDTO() = DialogmeldingBestillingDTO(
    behandlerRef = this.behandlerRef.toString(),
    personIdent = this.arbeidstakerPersonIdent.value,
    dialogmeldingUuid = this.uuid.toString(),
    dialogmeldingRefParent = this.parentRef?.toString(),
    dialogmeldingRefConversation = this.conversationRef.toString(),
    dialogmeldingType = this.getDialogmeldingType().name,
    dialogmeldingKodeverk = this.getDialogmeldingKodeverk().name,
    dialogmeldingKode = this.getDialogmeldingKode().value,
    dialogmeldingTekst = this.document.serialize(),
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
