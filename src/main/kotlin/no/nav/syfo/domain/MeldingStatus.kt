package no.nav.syfo.domain

import no.nav.syfo.api.models.MeldingStatusDTO
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
