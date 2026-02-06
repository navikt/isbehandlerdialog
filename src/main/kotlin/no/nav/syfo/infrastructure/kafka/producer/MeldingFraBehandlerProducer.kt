package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class MeldingFraBehandlerProducer(
    private val producer: KafkaProducer<String, KafkaMeldingDTO>,
) {
    fun sendMeldingFraBehandler(
        kafkaMeldingDTO: KafkaMeldingDTO,
        key: UUID,
    ) {
        try {
            producer.send(
                ProducerRecord(
                    MELDING_FRA_BEHANDLER_TOPIC,
                    key.toString(),
                    kafkaMeldingDTO,
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send melding fra behandler with id $key: ${e.message}",
                key,
                e
            )
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MeldingFraBehandlerProducer::class.java)
        const val MELDING_FRA_BEHANDLER_TOPIC = "teamsykefravr.melding-fra-behandler"
    }
}
