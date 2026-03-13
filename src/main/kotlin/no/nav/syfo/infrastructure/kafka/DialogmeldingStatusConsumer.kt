package no.nav.syfo.infrastructure.kafka

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ITransaction
import no.nav.syfo.application.ITransactionManager
import no.nav.syfo.application.MeldingService
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.repository.MeldingRepository
import no.nav.syfo.infrastructure.database.updateMeldingStatus
import no.nav.syfo.infrastructure.kafka.config.KafkaConsumerService
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

class DialogmeldingStatusConsumer(
    private val transactionManager: ITransactionManager,
    private val meldingRepository: MeldingRepository,
    private val meldingService: MeldingService,
) : KafkaConsumerService<KafkaDialogmeldingStatusDTO> {
    override val pollDurationInMillis: Long = 1000
    override suspend fun pollAndProcessRecords(consumer: KafkaConsumer<String, KafkaDialogmeldingStatusDTO>) {
        val records = consumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(
                records = records,
            )
            consumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, KafkaDialogmeldingStatusDTO>) {
        transactionManager.run { transaction ->
            records.forEach {
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_READ.increment()
                val kafkaDialogmeldingStatus = it.value()
                if (kafkaDialogmeldingStatus != null) {
                    runBlocking {
                        processDialogmeldingStatus(
                            kafkaDialogmeldingStatus = kafkaDialogmeldingStatus,
                            transaction = transaction,
                        )
                    }
                } else {
                    COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_TOMBSTONE.increment()
                    log.warn("Received KafkaDialogmeldingStatusDTO with no value: could be tombstone")
                }
            }
        }
    }

    private suspend fun processDialogmeldingStatus(
        kafkaDialogmeldingStatus: KafkaDialogmeldingStatusDTO,
        transaction: ITransaction,
    ) {
        val meldingUuid = UUID.fromString(kafkaDialogmeldingStatus.bestillingUuid)
        val meldingId = meldingRepository.getMelding(uuid = meldingUuid)?.id
        if (meldingId != null) {
            log.info("Received KafkaDialogmeldingStatusDTO for known melding: meldingUuid $meldingUuid")
            createOrUpdateMeldingStatus(
                transaction = transaction,
                meldingId = meldingId,
                kafkaDialogmeldingStatus = kafkaDialogmeldingStatus,
            )
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_SKIPPED.increment()
        }
    }

    private fun createOrUpdateMeldingStatus(
        transaction: ITransaction,
        meldingId: PMelding.Id,
        kafkaDialogmeldingStatus: KafkaDialogmeldingStatusDTO,
    ) {
        val existingMeldingStatus = meldingService.getMeldingStatus(meldingId = meldingId, transaction = transaction)
        if (existingMeldingStatus != null) {
            val updatedMeldingStatus = existingMeldingStatus.copy(
                status = MeldingStatusType.valueOf(kafkaDialogmeldingStatus.status),
                tekst = kafkaDialogmeldingStatus.tekst,
            )
            transaction.connection.updateMeldingStatus(meldingStatus = updatedMeldingStatus)
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_UPDATED.increment()
        } else {
            val meldingStatus = kafkaDialogmeldingStatus.toMeldingStatus()
            transaction.connection.createMeldingStatus(meldingStatus = meldingStatus, meldingId = meldingId)
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_CREATED.increment()
        }
        if (kafkaDialogmeldingStatus.status == MeldingStatusType.AVVIST.name) {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_AVVIST.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmeldingStatusConsumer::class.java)
    }
}
