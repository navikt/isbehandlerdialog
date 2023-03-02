package no.nav.syfo.melding.domain

import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.UUID

data class MeldingFraBehandler(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val type: DialogmeldingType,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val mottattTidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: PersonIdent,
    val behandlerPersonIdent: PersonIdent?,
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
        tidspunkt = mottattTidspunkt,
        arbeidstakerPersonIdent = arbeidstakerPersonIdent.value,
        behandlerPersonIdent = behandlerPersonIdent?.value,
        behandlerRef = null,
        tekst = tekst,
        antallVedlegg = antallVedlegg,
    )
