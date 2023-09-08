package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.DocumentComponentDTO
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.status.domain.MeldingStatusType
import java.time.OffsetDateTime
import java.util.*

data class MeldingResponseDTO(
    val conversations: Map<UUID, List<MeldingDTO>>
)

data class MeldingDTO(
    val uuid: UUID,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val behandlerRef: UUID,
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
)

data class MeldingStatusDTO(
    val type: MeldingStatusType,
    val tekst: String?,
)
