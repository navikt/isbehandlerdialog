package no.nav.syfo.infrastructure.kafka.domain

import java.time.OffsetDateTime

data class KafkaMeldingDTO(
    val uuid: String,
    val personIdent: String,
    val type: String,
    val conversationRef: String,
    val parentRef: String?,
    val msgId: String?,
    val tidspunkt: OffsetDateTime,
    val behandlerPersonIdent: String?,
)
