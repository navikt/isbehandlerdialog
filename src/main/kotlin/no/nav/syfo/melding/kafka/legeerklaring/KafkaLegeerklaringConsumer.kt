package no.nav.syfo.melding.kafka.legeerklaring

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.domain.MeldingType
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

class KafkaLegeerklaringConsumer(
    private val database: DatabaseInterface,
) : KafkaConsumerService<KafkaLegeerklaringDTO> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaLegeerklaringDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(
                consumerRecords = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(consumerRecords: ConsumerRecords<String, KafkaLegeerklaringDTO>) {
        database.connection.use { connection ->
            consumerRecords.forEach {
                COUNT_KAFKA_CONSUMER_LEGEERKLARING_READ.increment()
                val kafkaLegeerklaring = it.value()
                if (kafkaLegeerklaring != null) {
                    handleIncomingLegeerklaring(
                        kafkaLegeerklaring = kafkaLegeerklaring,
                        connection = connection,
                    )
                } else {
                    COUNT_KAFKA_CONSUMER_LEGEERKLARING_TOMBSTONE.increment()
                    log.warn("Received KafkaLegeerklaringDTO with no value: could be tombstone")
                }
            }
            connection.commit()
        }
    }

    private fun handleIncomingLegeerklaring(
        kafkaLegeerklaring: KafkaLegeerklaringDTO,
        connection: Connection,
    ) {
        val conversationRef = kafkaLegeerklaring.conversationRef?.refToConversation
        if (conversationRef != null) {
            handleIncomingLegeerklaringWithConversationRef(
                kafkaLegeerklaring = kafkaLegeerklaring,
                connection = connection,
                conversationRef = conversationRef,
            )
        } else {
            handleIncomingLegeerklaringWithoutConversationRef(
                kafkaLegeerklaring = kafkaLegeerklaring,
                connection = connection,
            )
        }
    }

    private fun handleIncomingLegeerklaringWithConversationRef(
        kafkaLegeerklaring: KafkaLegeerklaringDTO,
        connection: Connection,
        conversationRef: String,
    ) {
        val arbeidstakerPersonIdent = PersonIdent(kafkaLegeerklaring.personNrPasient)
        if (
            connection.hasSendtMeldingForConversationRefAndArbeidstakerIdent(
                conversationRef = UUID.fromString(conversationRef),
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            )
        ) {
            connection.createMeldingFraBehandler(
                meldingFraBehandler = kafkaLegeerklaring.toMeldingFraBehandler(),
            )
            COUNT_KAFKA_CONSUMER_LEGEERKLARING_STORED.increment()
        }
    }

    private fun handleIncomingLegeerklaringWithoutConversationRef(
        kafkaLegeerklaring: KafkaLegeerklaringDTO,
        connection: Connection,
    ) {
        val arbeidstakerPersonIdent = PersonIdent(kafkaLegeerklaring.personNrPasient)
        val utgaaende = connection.getUtgaendeMeldingerWithType(
            meldingType = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
            arbeidstakerPersonIdent = arbeidstakerPersonIdent
        ).lastOrNull()
        if (utgaaende != null && utgaaende.tidspunkt > OffsetDateTime.now().minusMonths(2)) {
            connection.createMeldingFraBehandler(
                meldingFraBehandler = kafkaLegeerklaring.toMeldingFraBehandler().copy(
                    conversationRef = utgaaende.conversationRef,
                ),
            )
            COUNT_KAFKA_CONSUMER_LEGEERKLARING_STORED.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaLegeerklaringConsumer::class.java)
    }
}
