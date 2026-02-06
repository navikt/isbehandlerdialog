package no.nav.syfo.infrastructure.kafka.domain

import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.serialize

data class DialogmeldingBestillingDTO(
    val behandlerRef: String,
    val personIdent: String,
    val dialogmeldingUuid: String,
    val dialogmeldingRefParent: String?,
    val dialogmeldingRefConversation: String,
    val dialogmeldingType: String,
    val dialogmeldingKodeverk: String,
    val dialogmeldingKode: Int,
    val dialogmeldingTekst: String?,
    val dialogmeldingVedlegg: ByteArray? = null,
    val kilde: String?,
) {
    companion object {
        fun from(meldingTilBehandler: Melding.MeldingTilBehandler, meldingPdf: ByteArray) =
            DialogmeldingBestillingDTO(
                behandlerRef = meldingTilBehandler.behandlerRef.toString(),
                personIdent = meldingTilBehandler.arbeidstakerPersonIdent.value,
                dialogmeldingUuid = meldingTilBehandler.uuid.toString(),
                dialogmeldingRefParent = meldingTilBehandler.parentRef?.toString(),
                dialogmeldingRefConversation = meldingTilBehandler.conversationRef.toString(),
                dialogmeldingType = meldingTilBehandler.getDialogmeldingType().name,
                dialogmeldingKodeverk = meldingTilBehandler.getDialogmeldingKodeverk().name,
                dialogmeldingKode = meldingTilBehandler.getDialogmeldingKode().value,
                dialogmeldingTekst = meldingTilBehandler.document.serialize(),
                dialogmeldingVedlegg = meldingPdf,
                kilde = "SYFO",
            )
    }
}
