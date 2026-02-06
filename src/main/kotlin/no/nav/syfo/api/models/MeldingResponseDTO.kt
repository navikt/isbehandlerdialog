package no.nav.syfo.api.models

import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.MOTTATT_LEGEERKLARING_TEKST
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.domain.MeldingType
import java.time.OffsetDateTime
import java.util.*

data class MeldingResponseDTO(
    val conversations: Map<UUID, List<MeldingDTO>>,
)

data class MeldingDTO(
    val uuid: UUID,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val behandlerRef: UUID?,
    val behandlerNavn: String?,
    val tekst: String,
    val document: List<DocumentComponentDTO>,
    val tidspunkt: OffsetDateTime,
    val innkommende: Boolean,
    val type: MeldingType,
    val antallVedlegg: Int,
    val status: MeldingStatusDTO?,
    val veilederIdent: String?,
    val isFirstVedleggLegeerklaring: Boolean,
) {
    companion object {
        fun from(meldingTilBehandler: Melding.MeldingTilBehandler, status: MeldingStatus?) =
            MeldingDTO(
                uuid = meldingTilBehandler.uuid,
                conversationRef = meldingTilBehandler.conversationRef,
                parentRef = meldingTilBehandler.parentRef,
                behandlerRef = meldingTilBehandler.behandlerRef,
                behandlerNavn = null,
                tekst = meldingTilBehandler.tekst ?: "",
                document = meldingTilBehandler.document,
                tidspunkt = meldingTilBehandler.tidspunkt,
                innkommende = false,
                type = meldingTilBehandler.type,
                antallVedlegg = meldingTilBehandler.antallVedlegg,
                status = status?.let { MeldingStatusDTO.from(it) },
                veilederIdent = meldingTilBehandler.veilederIdent,
                isFirstVedleggLegeerklaring = false,
            )

        fun from(meldingFraBehandler: Melding.MeldingFraBehandler, behandlerRef: UUID?) =
            MeldingDTO(
                uuid = meldingFraBehandler.uuid,
                conversationRef = meldingFraBehandler.conversationRef,
                parentRef = meldingFraBehandler.parentRef,
                behandlerRef = behandlerRef,
                behandlerNavn = meldingFraBehandler.behandlerNavn,
                tekst = meldingFraBehandler.tekst ?: "",
                document = emptyList(),
                tidspunkt = meldingFraBehandler.tidspunkt,
                innkommende = true,
                type = meldingFraBehandler.type,
                antallVedlegg = meldingFraBehandler.antallVedlegg,
                status = null,
                veilederIdent = meldingFraBehandler.veilederIdent,
                isFirstVedleggLegeerklaring = meldingFraBehandler.type == MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING && meldingFraBehandler.tekst == MOTTATT_LEGEERKLARING_TEKST
            )
    }
}

data class MeldingStatusDTO(
    val type: MeldingStatusType,
    val tekst: String?,
) {
    companion object {
        fun from(meldingStatus: MeldingStatus) =
            MeldingStatusDTO(
                type = meldingStatus.status,
                tekst = meldingStatus.tekst,
            )
    }
}
