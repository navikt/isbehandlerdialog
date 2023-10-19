package no.nav.syfo.melding.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingDTO
import no.nav.syfo.melding.kafka.domain.KafkaMeldingDTO
import java.time.OffsetDateTime
import java.util.UUID

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
    val innkommendePublishedAt: OffsetDateTime?
) : Melding {
    override val behandlerRef: UUID? = null
    override val innkommende: Boolean = true
    override val document: List<DocumentComponentDTO> = emptyList()
    override val journalpostId: String? = null
    override val veilederIdent: String? = null
}

fun MeldingFraBehandler.toMeldingDTO(behandlerRef: UUID?) = MeldingDTO(
    uuid = uuid,
    conversationRef = conversationRef,
    parentRef = parentRef,
    behandlerRef = behandlerRef,
    behandlerNavn = behandlerNavn,
    tekst = tekst ?: "",
    document = emptyList(),
    tidspunkt = tidspunkt,
    innkommende = true,
    type = type,
    antallVedlegg = antallVedlegg,
    status = null,
    veilederIdent = veilederIdent,
    isFirstVedleggLegeerklaring = type == MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING && tekst == MOTTATT_LEGEERKLARING_TEKST
)

fun MeldingFraBehandler.toKafkaMeldingDTO() = KafkaMeldingDTO(
    uuid = uuid.toString(),
    personIdent = arbeidstakerPersonIdent.value,
    type = type.name,
    conversationRef = conversationRef.toString(),
    parentRef = parentRef?.toString(),
    msgId = msgId,
    tidspunkt = tidspunkt,
    behandlerPersonIdent = behandlerPersonIdent?.value,
)
