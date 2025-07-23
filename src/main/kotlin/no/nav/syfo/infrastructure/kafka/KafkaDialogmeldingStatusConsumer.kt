package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.config.KafkaConsumerService
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.getMelding
import no.nav.syfo.infrastructure.database.updateMeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.UUID

class KafkaDialogmeldingStatusConsumer(
    private val database: DatabaseInterface,
    private val meldingService: MeldingService,
) : KafkaConsumerService<KafkaDialogmeldingStatusDTO> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaDialogmeldingStatusDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(
                consumerRecords = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(consumerRecords: ConsumerRecords<String, KafkaDialogmeldingStatusDTO>) {
        database.connection.use { connection ->
            consumerRecords.forEach {
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_READ.increment()
                val kafkaDialogmeldingStatus = it.value()
                if (kafkaDialogmeldingStatus != null) {
                    processDialogmeldingStatus(
                        kafkaDialogmeldingStatus = kafkaDialogmeldingStatus,
                        connection = connection,
                    )
                } else {
                    COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_TOMBSTONE.increment()
                    log.warn("Received KafkaDialogmeldingStatusDTO with no value: could be tombstone")
                }
            }
            connection.commit()
        }
    }

    private fun processDialogmeldingStatus(
        kafkaDialogmeldingStatus: KafkaDialogmeldingStatusDTO,
        connection: Connection,
    ) {
        val meldingUuid = UUID.fromString(kafkaDialogmeldingStatus.bestillingUuid)
        val meldingId = database.getMelding(uuid = meldingUuid)?.id
        if (meldingId != null) {
            log.info("Received KafkaDialogmeldingStatusDTO for known melding: meldingUuid $meldingUuid")
            createOrUpdateMeldingStatus(
                connection = connection,
                meldingId = meldingId,
                kafkaDialogmeldingStatus = kafkaDialogmeldingStatus,
            )
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_SKIPPED.increment()
        }
    }

    private fun createOrUpdateMeldingStatus(
        connection: Connection,
        meldingId: PMelding.Id,
        kafkaDialogmeldingStatus: KafkaDialogmeldingStatusDTO,
    ) {
        val existingMeldingStatus = meldingService.getMeldingStatus(meldingId = meldingId, connection = connection)
        if (existingMeldingStatus != null) {
            val updatedMeldingStatus = existingMeldingStatus.copy(
                status = MeldingStatusType.valueOf(kafkaDialogmeldingStatus.status),
                tekst = kafkaDialogmeldingStatus.tekst,
            )
            connection.updateMeldingStatus(meldingStatus = updatedMeldingStatus)
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_UPDATED.increment()
        } else {
            val meldingStatus = kafkaDialogmeldingStatus.toMeldingStatus()
            connection.createMeldingStatus(meldingStatus = meldingStatus, meldingId = meldingId)
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_CREATED.increment()
        }
        if (kafkaDialogmeldingStatus.status == MeldingStatusType.AVVIST.name) {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_AVVIST.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaDialogmeldingStatusConsumer::class.java)
    }
}
