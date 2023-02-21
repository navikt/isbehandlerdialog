package no.nav.syfo.dialogmelding.kafka

import java.time.LocalDateTime

data class KafkaDialogmeldingFromBehandlerDTO(
    val msgId: String,
    val navLogId: String,
    val mottattTidspunkt: LocalDateTime,
    val personIdentPasient: String,
    val personIdentBehandler: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorOrgName: String,
    val legehpr: String?,
    val fellesformatXML: String,
)
