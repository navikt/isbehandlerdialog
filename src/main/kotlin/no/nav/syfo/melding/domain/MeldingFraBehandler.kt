package no.nav.syfo.melding.domain

import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.Melding
import java.time.OffsetDateTime
import java.util.UUID

data class MeldingFraBehandler(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val type: MeldingType,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val msgId: String,
    val mottattTidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: PersonIdent,
    val behandlerPersonIdent: PersonIdent?,
    val behandlerNavn: String?,
    val tekst: String?,
    val antallVedlegg: Int,
)

fun MeldingFraBehandler.toPMelding() =
    PMelding(
        uuid = uuid,
        createdAt = createdAt,
        innkommende = true,
        type = type.name,
        conversationRef = conversationRef,
        parentRef = parentRef,
        msgId = msgId,
        tidspunkt = mottattTidspunkt,
        arbeidstakerPersonIdent = arbeidstakerPersonIdent.value,
        behandlerPersonIdent = behandlerPersonIdent?.value,
        behandlerNavn = behandlerNavn,
        behandlerRef = null,
        tekst = tekst,
        document = emptyList(),
        antallVedlegg = antallVedlegg,
    )

fun MeldingFraBehandler.toMelding(behandlerRef: UUID) = Melding(
    behandlerRef = behandlerRef,
    behandlerNavn = behandlerNavn,
    tekst = tekst ?: "",
    document = emptyList(),
    tidspunkt = mottattTidspunkt,
    innkommende = true,
)
