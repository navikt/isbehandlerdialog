package no.nav.syfo.melding.kafka.legeerklaring

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.Storage
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

class KafkaLegeerklaringConsumer(
    private val database: DatabaseInterface,
    private val storage: Storage,
    private val bucketName: String,
    private val bucketNameVedlegg: String,
    private val pdfgenClient: PdfGenClient,
) : KafkaConsumerService<KafkaLegeerklaeringMessage> {
    override val pollDurationInMillis: Long = 1000
    private val mapper = configuredJacksonMapper()

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaLegeerklaeringMessage>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(
                consumerRecords = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(consumerRecords: ConsumerRecords<String, KafkaLegeerklaeringMessage>) {
        database.connection.use { connection ->
            consumerRecords.forEach {
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
                            connection = connection,
                        )
                    }
                } else {
                    COUNT_KAFKA_CONSUMER_LEGEERKLARING_TOMBSTONE.increment()
                    log.warn("Received KafkaLegeerklaringDTO with no value: could be tombstone")
                }
            }
            connection.commit()
        }
    }

    private fun handleIncomingLegeerklaring(
        legeerklaring: LegeerklaringDTO,
        vedleggIds: List<String>,
        connection: Connection,
    ) {
        val conversationRef = legeerklaring.conversationRef?.refToConversation
        if (conversationRef != null) {
            handleIncomingLegeerklaringWithConversationRef(
                legeerklaring = legeerklaring,
                vedleggIds = vedleggIds,
                connection = connection,
                conversationRef = conversationRef,
            )
        } else {
            handleIncomingLegeerklaringWithoutConversationRef(
                legeerklaring = legeerklaring,
                vedleggIds = vedleggIds,
                connection = connection,
            )
        }
    }

    private fun handleIncomingLegeerklaringWithConversationRef(
        legeerklaring: LegeerklaringDTO,
        vedleggIds: List<String>,
        connection: Connection,
        conversationRef: String,
    ) {
        val arbeidstakerPersonIdent = PersonIdent(legeerklaring.personNrPasient)
        val utgaaende = connection.getUtgaendeMeldingerInConversation(
            conversationRef = UUID.fromString(conversationRef),
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
        ).lastOrNull()
        if (utgaaende != null) {
            val pdfVedlegg = getPDFVedlegg(legeerklaring, vedleggIds)
            val meldingId = connection.createMeldingFraBehandler(
                meldingFraBehandler = legeerklaring.toMeldingFraBehandler(
                    parentRef = utgaaende.uuid,
                    antallVedlegg = pdfVedlegg.size,
                ),
            )
            storeVedlegg(
                meldingId = meldingId,
                vedlegg = pdfVedlegg,
                connection = connection,
            )
            COUNT_KAFKA_CONSUMER_LEGEERKLARING_STORED.increment()
        }
    }

    private fun handleIncomingLegeerklaringWithoutConversationRef(
        legeerklaring: LegeerklaringDTO,
        vedleggIds: List<String>,
        connection: Connection,
    ) {
        val arbeidstakerPersonIdent = PersonIdent(legeerklaring.personNrPasient)
        val utgaaende = connection.getUtgaendeMeldingerWithType(
            meldingType = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
            arbeidstakerPersonIdent = arbeidstakerPersonIdent
        ).lastOrNull()

        if (utgaaende != null && utgaaende.tidspunkt > OffsetDateTime.now().minusMonths(2)) {
            val pdfVedlegg = getPDFVedlegg(legeerklaring, vedleggIds)
            val meldingId = connection.createMeldingFraBehandler(
                meldingFraBehandler = legeerklaring.toMeldingFraBehandler(
                    parentRef = utgaaende.uuid,
                    antallVedlegg = pdfVedlegg.size,
                ).copy(
                    conversationRef = utgaaende.conversationRef,
                ),
            )
            storeVedlegg(
                meldingId = meldingId,
                vedlegg = pdfVedlegg,
                connection = connection,
            )
            COUNT_KAFKA_CONSUMER_LEGEERKLARING_STORED.increment()
        }
    }

    private fun getPDFVedlegg(
        legeerklaring: LegeerklaringDTO,
        vedlegg: List<String>,
    ): List<ByteArray> {
        val legeerklaringPdf = runBlocking {
            pdfgenClient.generateLegeerklaring(legeerklaring)
        }
        return mutableListOf(legeerklaringPdf!!).also {
            it.addAll(
                vedlegg.map { id ->
                    getVedlegg(id)
                }.filter {
                    it.vedlegg.type == "application/pdf"
                }.map {
                    it.getBytes()
                }
            )
        }
    }

    private fun storeVedlegg(
        meldingId: PMelding.Id,
        vedlegg: List<ByteArray>,
        connection: Connection,
    ) {
        vedlegg.forEachIndexed { index, pdf ->
            connection.createVedlegg(
                pdf = pdf,
                meldingId = meldingId,
                number = index,
                commit = false,
            )
        }
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
        private val log = LoggerFactory.getLogger(KafkaLegeerklaringConsumer::class.java)
    }
}
