package no.nav.syfo.melding.kafka

import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.toDialogmeldingBestillingDTO
import no.nav.syfo.melding.kafka.domain.DialogmeldingBestillingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class DialogmeldingBestillingProducer(
    private val dialogmeldingBestillingKafkaProducer: KafkaProducer<String, DialogmeldingBestillingDTO>,
    private val produceDialogmeldingBestillingEnabled: Boolean,
) {
    fun sendDialogmeldingBestilling(meldingTilBehandler: MeldingTilBehandler) {
        val dialogmeldingBestillingDTO = meldingTilBehandler.toDialogmeldingBestillingDTO()
        val key = dialogmeldingBestillingDTO.dialogmeldingRefConversation
        try {
            if (produceDialogmeldingBestillingEnabled) {
                dialogmeldingBestillingKafkaProducer.send(
                    ProducerRecord(
                        BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC,
                        key,
                        dialogmeldingBestillingDTO
                    )
                ).get()
            } else {
                log.info("Would have sent behandler-dialogmelding-bestilling if enabled: $key")
            }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send behandler-dialogmelding-bestilling with key {}: ${e.message}",
                key,
                e
            )
            throw e
        }
    }

    companion object {
        const val BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC =
            "teamsykefravr.isdialogmelding-behandler-dialogmelding-bestilling"
        private val log = LoggerFactory.getLogger(DialogmeldingBestillingProducer::class.java)
    }
}
