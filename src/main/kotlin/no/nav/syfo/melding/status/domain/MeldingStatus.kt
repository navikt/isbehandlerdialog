package no.nav.syfo.melding.status.domain

import no.nav.syfo.melding.api.MeldingStatusDTO
import java.util.*

enum class MeldingStatusType {
    BESTILT, SENDT, OK, AVVIST
}

data class MeldingStatus(
    val uuid: UUID,
    val status: MeldingStatusType,
    val tekst: String?,
)

fun MeldingStatus.toMeldingStatusDTO() = MeldingStatusDTO(
    type = status,
    tekst = tekst,
)
