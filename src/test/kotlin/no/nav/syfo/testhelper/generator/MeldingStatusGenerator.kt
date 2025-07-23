package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.infrastructure.kafka.KafkaDialogmeldingStatusDTO
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

fun generateMeldingStatus(
    meldingUUID: UUID = UUID.randomUUID(),
    status: MeldingStatusType,
    tekst: String? = null,
) = MeldingStatus(
    uuid = meldingUUID,
    status = status,
    tekst = tekst,
)
