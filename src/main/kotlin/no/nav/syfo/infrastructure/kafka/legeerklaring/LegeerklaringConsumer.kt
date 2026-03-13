package no.nav.syfo.infrastructure.kafka.legeerklaring

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.Storage
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ITransaction
import no.nav.syfo.application.ITransactionManager
import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.application.IPdfGenClient
import no.nav.syfo.application.MeldingService
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.getUtgaendeMeldingerInConversation
import no.nav.syfo.infrastructure.kafka.config.KafkaConsumerService
import no.nav.syfo.infrastructure.kafka.domain.KafkaLegeerklaeringMessage
import no.nav.syfo.infrastructure.kafka.domain.Status
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

class LegeerklaringConsumer(
    private val transactionManager: ITransactionManager,
    private val meldingRepository: IMeldingRepository,
    private val storage: Storage,
    private val bucketName: String,
    private val bucketNameVedlegg: String,
    private val pdfgenClient: IPdfGenClient,
    private val meldingService: MeldingService,
) : KafkaConsumerService<KafkaLegeerklaeringMessage> {
    override val pollDurationInMillis: Long = 1000
    private val mapper = configuredJacksonMapper()

    override suspend fun pollAndProcessRecords(consumer: KafkaConsumer<String, KafkaLegeerklaeringMessage>) {
        val records = consumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(records = records)
            consumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, KafkaLegeerklaeringMessage>) {
        transactionManager.run { transaction ->
            records.forEach {
                COUNT_KAFKA_CONSUMER_LEGEERKLARING_READ.increment()
                val kafkaLegeerklaring = it.value()
                if (kafkaLegeerklaring != null) {
                    log.info(
                        "Received legeerklaring: ${kafkaLegeerklaring.legeerklaeringObjectId} with " +
                            "status ${kafkaLegeerklaring.validationResult.status} " +
                            "and with ${kafkaLegeerklaring.vedlegg?.size ?: 0} vedlegg"
                    )
                    if (kafkaLegeerklaring.validationResult.status == Status.OK) {
                        val legeerklaring = getLegeerklaring(kafkaLegeerklaring.legeerklaeringObjectId)
                        handleIncomingLegeerklaring(
                            legeerklaring = legeerklaring,
                            vedleggIds = kafkaLegeerklaring.vedlegg ?: emptyList(),
                            transaction = transaction,
                        )
                    }
                } else {
                    COUNT_KAFKA_CONSUMER_LEGEERKLARING_TOMBSTONE.increment()
                    log.warn("Received KafkaLegeerklaringDTO with no value: could be tombstone")
                }
            }
        }
    }

    private fun handleIncomingLegeerklaring(
        legeerklaring: LegeerklaringDTO,
        vedleggIds: List<String>,
        transaction: ITransaction,
    ) {
        val conversationRef = legeerklaring.conversationRef?.refToConversation
        if (conversationRef != null) {
            handleIncomingLegeerklaringWithConversationRef(
                legeerklaring = legeerklaring,
                vedleggIds = vedleggIds,
                transaction = transaction,
                conversationRef = conversationRef,
            )
        } else {
            handleIncomingLegeerklaringWithoutConversationRef(
                legeerklaring = legeerklaring,
                vedleggIds = vedleggIds,
                transaction = transaction,
            )
        }
    }

    private fun handleIncomingLegeerklaringWithConversationRef(
        legeerklaring: LegeerklaringDTO,
        vedleggIds: List<String>,
        transaction: ITransaction,
        conversationRef: String,
    ) {
        val utgaaende = transaction.connection.getUtgaendeMeldingerInConversation(
            conversationRef = UUID.fromString(conversationRef),
            arbeidstakerPersonIdent = PersonIdent(legeerklaring.personNrPasient),
            type = Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
        ).lastOrNull()
        if (utgaaende != null) {
            val pdfVedlegg = getPDFVedlegg(legeerklaring, vedleggIds)
            val melding = meldingService.createMeldingFraBehandler(
                meldingFraBehandler = legeerklaring.toMeldingFraBehandler(
                    parentRef = utgaaende.uuid,
                    antallVedlegg = pdfVedlegg.size,
                ),
                connection = transaction.connection,
            )
            meldingService.lagreVedlegg(
                meldingId = melding.id,
                vedlegg = pdfVedlegg,
                transaction = transaction,
            )
            COUNT_KAFKA_CONSUMER_LEGEERKLARING_WITH_CONVREF_STORED.increment()
        }
    }

    private fun handleIncomingLegeerklaringWithoutConversationRef(
        legeerklaring: LegeerklaringDTO,
        vedleggIds: List<String>,
        transaction: ITransaction,
    ) {
        val utgaaende = meldingRepository.getUtgaendeMeldingerWithType(
            meldingType = Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
            arbeidstakerPersonIdent = legeerklaring.personNrPasient,
            connection = transaction.connection,
        ).lastOrNull()

        if (utgaaende != null && utgaaende.tidspunkt > OffsetDateTime.now().minusMonths(2)) {
            val pdfVedlegg = getPDFVedlegg(legeerklaring, vedleggIds)
            val melding = meldingService.createMeldingFraBehandler(
                meldingFraBehandler = legeerklaring.toMeldingFraBehandler(
                    parentRef = utgaaende.uuid,
                    antallVedlegg = pdfVedlegg.size,
                ).copy(
                    conversationRef = utgaaende.conversationRef,
                ),
                connection = transaction.connection,
            )
            meldingService.lagreVedlegg(
                meldingId = melding.id,
                vedlegg = pdfVedlegg,
                transaction = transaction,
            )
            COUNT_KAFKA_CONSUMER_LEGEERKLARING_WITHOUT_CONVREF_STORED.increment()
        }
    }

    private fun getPDFVedlegg(
        legeerklaring: LegeerklaringDTO,
        vedlegg: List<String>,
    ): List<ByteArray> {
        val legeerklaringPdf =
            runBlocking { pdfgenClient.generateLegeerklaring(legeerklaring) }!!
        val otherVedlegg = vedlegg.map { id -> getVedlegg(id) }
            .filter { it.vedlegg.type == "application/pdf" }
            .map { it.getBytes() }
        return listOf(legeerklaringPdf) + otherVedlegg
    }

    private fun getLegeerklaring(objectId: String): LegeerklaringDTO =
        storage.get(bucketName, objectId)?.let { blob ->
            mapper.readValue(blob.getContent())
        } ?: throw RuntimeException("Fant ikke legeerklaring i gcp bucket: $objectId")

    private fun getVedlegg(objectId: String): LegeerklaringVedleggDTO =
        storage.get(bucketNameVedlegg, objectId)?.let { blob ->
            mapper.readValue(blob.getContent())
        } ?: throw RuntimeException("Fant ikke vedlegg for legeerklaring i gcp bucket: $objectId")

    companion object {
        private val log = LoggerFactory.getLogger(LegeerklaringConsumer::class.java)
    }
}
