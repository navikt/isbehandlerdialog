package no.nav.syfo.domain

import java.util.*

enum class MeldingStatusType {
    BESTILT, SENDT, OK, AVVIST
}

data class MeldingStatus(
    val uuid: UUID,
    val status: MeldingStatusType,
    val tekst: String?,
)
