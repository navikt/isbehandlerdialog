package no.nav.syfo.melding.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingDTO
import java.time.OffsetDateTime
import java.util.UUID

data class MeldingFraBehandler(
    override val uuid: UUID,
    val createdAt: OffsetDateTime,
    override val type: MeldingType,
    override val conversationRef: UUID,
    override val parentRef: UUID?,
    override val msgId: String,
    override val tidspunkt: OffsetDateTime,
    override val arbeidstakerPersonIdent: PersonIdent,
    override val behandlerPersonIdent: PersonIdent?,
    override val behandlerNavn: String?,
    override val tekst: String?,
    override val antallVedlegg: Int,
) : Melding {
    override val behandlerRef: UUID? = null
    override val innkommende: Boolean = true
    override val document: List<DocumentComponentDTO> = emptyList()
    override val journalpostId: String? = null
}

fun MeldingFraBehandler.toMeldingDTO(behandlerRef: UUID) = MeldingDTO(
    uuid = uuid,
    behandlerRef = behandlerRef,
    behandlerNavn = behandlerNavn,
    tekst = tekst ?: "",
    document = emptyList(),
    tidspunkt = tidspunkt,
    innkommende = true,
    antallVedlegg = antallVedlegg,
    status = null,
)
