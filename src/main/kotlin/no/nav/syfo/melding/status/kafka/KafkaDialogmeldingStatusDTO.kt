package no.nav.syfo.melding.status.kafka

import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatusType
import java.time.OffsetDateTime
import java.util.*

data class KafkaDialogmeldingStatusDTO(
    val uuid: String,
    val createdAt: OffsetDateTime,
    val status: String,
    val tekst: String?,
    val bestillingUuid: String,
)

fun KafkaDialogmeldingStatusDTO.toMeldingStatus() = MeldingStatus(
    uuid = UUID.randomUUID(),
    status = MeldingStatusType.valueOf(status),
    tekst = tekst,
)
