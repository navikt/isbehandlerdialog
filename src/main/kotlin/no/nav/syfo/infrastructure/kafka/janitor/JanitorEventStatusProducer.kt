package no.nav.syfo.infrastructure.kafka.janitor

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class JanitorEventStatusProducer(
    private val producer: KafkaProducer<String, JanitorEventStatusDTO>,
) {
    fun sendEventStatus(eventStatus: JanitorEventStatusDTO) {
        try {
            producer.send(
                ProducerRecord(
                    JANITOR_EVENT_STATUS_TOPIC,
                    eventStatus.eventUUID,
                    eventStatus,
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send JanitorEventStatusDTO with id ${eventStatus.eventUUID}: ${e.message}",
                e,
            )
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(JanitorEventStatusProducer::class.java)
        const val JANITOR_EVENT_STATUS_TOPIC = "teamsykefravr.syfojanitor-status"
    }
}
