package no.nav.syfo.melding.status.domain

import java.util.*

enum class MeldingStatusType {
    BESTILT, SENDT, OK, AVVIST
}

data class MeldingStatus(
    val uuid: UUID,
    val status: MeldingStatusType,
    val tekst: String?,
)
