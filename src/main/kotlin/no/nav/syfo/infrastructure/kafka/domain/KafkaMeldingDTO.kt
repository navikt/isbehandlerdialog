package no.nav.syfo.infrastructure.kafka.domain

import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.domain.MeldingTilBehandler
import java.time.OffsetDateTime

data class KafkaMeldingDTO(
    val uuid: String,
    val personIdent: String,
    val type: String,
    val conversationRef: String,
    val parentRef: String?,
    val msgId: String?,
    val tidspunkt: OffsetDateTime,
    val behandlerPersonIdent: String?,
) {
    companion object {
        fun from(meldingTilBehandler: MeldingTilBehandler) =
            KafkaMeldingDTO(
                uuid = meldingTilBehandler.uuid.toString(),
                personIdent = meldingTilBehandler.arbeidstakerPersonIdent.value,
                type = meldingTilBehandler.type.name,
                conversationRef = meldingTilBehandler.conversationRef.toString(),
                parentRef = meldingTilBehandler.parentRef?.toString(),
                msgId = meldingTilBehandler.msgId,
                tidspunkt = meldingTilBehandler.tidspunkt,
                behandlerPersonIdent = meldingTilBehandler.behandlerPersonIdent?.value,
            )

        fun from(meldingFraBehandler: MeldingFraBehandler) =
            KafkaMeldingDTO(
                uuid = meldingFraBehandler.uuid.toString(),
                personIdent = meldingFraBehandler.arbeidstakerPersonIdent.value,
                type = meldingFraBehandler.type.name,
                conversationRef = meldingFraBehandler.conversationRef.toString(),
                parentRef = meldingFraBehandler.parentRef?.toString(),
                msgId = meldingFraBehandler.msgId,
                tidspunkt = meldingFraBehandler.tidspunkt,
                behandlerPersonIdent = meldingFraBehandler.behandlerPersonIdent?.value,
            )
    }
}
