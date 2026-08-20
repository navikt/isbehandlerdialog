package no.nav.syfo.infrastructure.kafka.janitor

import no.nav.syfo.application.JanitorService
import no.nav.syfo.infrastructure.kafka.config.KafkaConsumerService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

class JanitorEventConsumerService(
    private val janitorService: JanitorService,
) : KafkaConsumerService<JanitorEventDTO> {
    override val pollDurationInMillis: Long = 1000

    override suspend fun pollAndProcessRecords(consumer: KafkaConsumer<String, JanitorEventDTO>) {
        val records = consumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            records.requireNoNulls().forEach { record ->
                janitorService.handle(record.value())
            }
            consumer.commitSync()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(JanitorEventConsumerService::class.java)
    }
}
