package no.nav.syfo.domain

import java.time.OffsetDateTime
import java.util.*

const val MOTTATT_LEGEERKLARING_TEKST = "Mottatt legeerklæring"

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
    val innkommendePublishedAt: OffsetDateTime?,
) : Melding {
    override val behandlerRef: UUID? = null
    override val innkommende: Boolean = true
    override val document: List<DocumentComponentDTO> = emptyList()
    override val journalpostId: String? = null
    override val veilederIdent: String? = null
}
