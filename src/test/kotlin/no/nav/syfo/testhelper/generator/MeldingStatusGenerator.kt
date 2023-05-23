package no.nav.syfo.testhelper.generator

import no.nav.syfo.melding.status.domain.MeldingStatusType
import no.nav.syfo.melding.status.kafka.KafkaDialogmeldingStatusDTO
import java.time.OffsetDateTime
import java.util.UUID

fun generateKafkaDialogmeldingStatusDTO(
    meldingUUID: UUID,
    status: MeldingStatusType,
    tekst: String? = null,
): KafkaDialogmeldingStatusDTO = KafkaDialogmeldingStatusDTO(
    uuid = UUID.randomUUID().toString(),
    createdAt = OffsetDateTime.now(),
    status = status.name,
    tekst = tekst,
    bestillingUuid = meldingUUID.toString()
)
