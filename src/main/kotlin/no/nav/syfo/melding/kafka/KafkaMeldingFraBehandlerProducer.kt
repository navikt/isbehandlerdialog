package no.nav.syfo.melding.kafka

import no.nav.syfo.melding.kafka.domain.KafkaMeldingFraBehandlerDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class KafkaMeldingFraBehandlerProducer(
    private val kafkaMeldingFraBehandlerProducer: KafkaProducer<String, KafkaMeldingFraBehandlerDTO>,
) {
    fun sendMeldingFraBehandler(
        kafkaMeldingFraBehandlerDTO: KafkaMeldingFraBehandlerDTO,
        key: UUID,
    ) {
        try {
            kafkaMeldingFraBehandlerProducer.send(
                ProducerRecord(
                    MELDING_FRA_BEHANDLER_TOPIC,
                    key.toString(),
                    kafkaMeldingFraBehandlerDTO,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send kafkaMeldingFraBehandlerDTO with id $key: ${e.message}")
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaMeldingFraBehandlerProducer::class.java)
        const val MELDING_FRA_BEHANDLER_TOPIC = "teamsykefravr.melding-fra-behandler"
    }
}
