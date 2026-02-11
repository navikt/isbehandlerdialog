package no.nav.syfo.infrastructure.kafka.dialogmelding

import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.config.KafkaConsumerService
import no.nav.syfo.infrastructure.kafka.domain.KafkaDialogmeldingFraBehandlerDTO
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

class DialogmeldingFraBehandlerConsumer(
    private val database: DatabaseInterface,
    private val meldingService: MeldingService,
) : KafkaConsumerService<KafkaDialogmeldingFraBehandlerDTO> {

    override val pollDurationInMillis: Long = 1000

    override suspend fun pollAndProcessRecords(consumer: KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>) {
        val records = consumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processConsumerRecords(
                records = records,
            )
            consumer.commitSync()
        }
    }

    private fun processConsumerRecords(
        records: ConsumerRecords<String, KafkaDialogmeldingFraBehandlerDTO>,
    ) {
        database.connection.use { connection ->
            records.forEach {
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ.increment()
                val kafkaDialogmeldingFraBehandler = it.value()
                if (kafkaDialogmeldingFraBehandler != null) {
                    if (kafkaDialogmeldingFraBehandler.dialogmelding.innkallingMoterespons == null) {
                        meldingService.receiveDialogmeldingFromBehandler(
                            kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                            connection = connection,
                        )
                    } // else: dialogmøterelaterte meldinger konsumeres av isdialogmote
                } else {
                    COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_TOMBSTONE.increment()
                    log.warn("Received kafkaDialogmeldingFraBehandler with no value: could be tombstone")
                }
            }
            connection.commit()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmeldingFraBehandlerConsumer::class.java)
    }
}
