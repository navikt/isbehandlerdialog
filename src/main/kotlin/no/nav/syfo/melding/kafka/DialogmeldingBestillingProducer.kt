package no.nav.syfo.melding.kafka

import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.domain.toDialogmeldingBestillingDTO
import no.nav.syfo.melding.kafka.domain.DialogmeldingBestillingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class DialogmeldingBestillingProducer(
    private val dialogmeldingBestillingKafkaProducer: KafkaProducer<String, DialogmeldingBestillingDTO>,
) {
    fun sendDialogmeldingBestilling(meldingTilBehandler: MeldingTilBehandler, meldingPdf: ByteArray) {
        val dialogmeldingBestillingDTO = meldingTilBehandler.toDialogmeldingBestillingDTO(meldingPdf)
        val key = dialogmeldingBestillingDTO.dialogmeldingRefConversation
        try {
            dialogmeldingBestillingKafkaProducer.send(
                ProducerRecord(
                    BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC,
                    key,
                    dialogmeldingBestillingDTO
                )
            ).get()
            COUNT_KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_SENT.increment()
            if (meldingTilBehandler.type == MeldingType.FORESPORSEL_PASIENT_PAMINNELSE) {
                COUNT_KAFKA_CONSUMER_PAMINNELSE_BESTILLING_SENT.increment()
            }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send behandler-dialogmelding-bestilling with key {}: ${e.message}",
                key,
                e
            )
            COUNT_KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_ERROR.increment()
            throw e
        }
    }

    companion object {
        const val BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC =
            "teamsykefravr.isdialogmelding-behandler-dialogmelding-bestilling"
        private val log = LoggerFactory.getLogger(DialogmeldingBestillingProducer::class.java)
    }
}
