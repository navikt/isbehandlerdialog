package no.nav.syfo.melding.kafka.producer

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

            COUNT_KAFKA_PRODUCER_MELDING_TIL_BEHANDLER_BESTILLING_SENT.increment()
            when (meldingTilBehandler.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER -> COUNT_KAFKA_PRODUCER_FORESPORSEL_TILLEGGSOPPLYSNING_BESTILLING_SENT.increment()
                MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> COUNT_KAFKA_PRODUCER_FORESPORSEL_LEGEERKLARING_BESTILLING_SENT.increment()
                MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> COUNT_KAFKA_PRODUCER_PAMINNELSE_BESTILLING_SENT.increment()
                MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> COUNT_KAFKA_PRODUCER_RETUR_LEGEERKLARING_BESTILLING_SENT.increment()
                MeldingType.HENVENDELSE_MELDING_FRA_NAV -> COUNT_KAFKA_PRODUCER_MELDING_FRA_NAV_BESTILLING_SENT.increment()
                MeldingType.HENVENDELSE_MELDING_TIL_NAV -> {} // only used for incoming messages
            }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send behandler-dialogmelding-bestilling with key {}: ${e.message}",
                key,
                e
            )
            COUNT_KAFKA_PRODUCER_MELDING_TIL_BEHANDLER_BESTILLING_ERROR.increment()
            throw e
        }
    }

    companion object {
        const val BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC =
            "teamsykefravr.isdialogmelding-behandler-dialogmelding-bestilling"
        private val log = LoggerFactory.getLogger(DialogmeldingBestillingProducer::class.java)
    }
}
