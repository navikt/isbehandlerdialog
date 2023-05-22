package no.nav.syfo.melding.kafka

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.*
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.client.padm2.VedleggDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.kafka.domain.*
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.UUID

class KafkaDialogmeldingFraBehandlerConsumer(
    private val database: DatabaseInterface,
    private val padm2Client: Padm2Client,
) : KafkaConsumerService<KafkaDialogmeldingFraBehandlerDTO> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processConsumerRecords(
                consumerRecords = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processConsumerRecords(
        consumerRecords: ConsumerRecords<String, KafkaDialogmeldingFraBehandlerDTO>,
    ) {
        database.connection.use { connection ->
            consumerRecords.forEach {
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ.increment()
                val kafkaDialogmeldingFraBehandler = it.value()
                if (kafkaDialogmeldingFraBehandler != null) {
                    handleIncomingMessage(
                        kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                        connection = connection,
                    )
                } else {
                    COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_TOMBSTONE.increment()
                    log.warn("Received kafkaDialogmeldingFraBehandler with no value: could be tombstone")
                }
            }
            connection.commit()
        }
    }

    private fun handleIncomingMessage(
        kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        connection: Connection,
    ) {
        if (kafkaDialogmeldingFraBehandler.isForesporselSvarWithConversationRef()) {
            handleForesporselSvar(
                kafkaForesporselSvarFraBehandler = kafkaDialogmeldingFraBehandler,
                connection = connection,
            )
        } else if (kafkaDialogmeldingFraBehandler.isForesporselSvarWithoutConversationRef()) {
            log.warn("Received DIALOG_SVAR with missing conversationRef: msgId = ${kafkaDialogmeldingFraBehandler.msgId}")
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_CONVERSATION_REF_MISSING.increment()
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FORESPORSELSVAR.increment()
        }
    }

    private fun handleForesporselSvar(
        kafkaForesporselSvarFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        connection: Connection,
    ) {
        val conversationRef = UUID.fromString(kafkaForesporselSvarFraBehandler.conversationRef!!)
        if (
            connection.hasSendtMeldingForConversationRefAndArbeidstakerIdent(
                conversationRef = conversationRef,
                arbeidstakerPersonIdent = PersonIdent(kafkaForesporselSvarFraBehandler.personIdentPasient),
            )
        ) {
            if (connection.getMeldingForMsgId(kafkaForesporselSvarFraBehandler.msgId) == null) {
                log.info("Received a dialogmelding from behandler: $conversationRef")
                storeForesporselSvar(
                    connection = connection,
                    kafkaForesporselSvarFraBehandler = kafkaForesporselSvarFraBehandler,
                )
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_MELDING_CREATED.increment()
            } else {
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_DUPLICATE.increment()
                log.warn("Received duplicate dialogmelding from behandler: $conversationRef")
            }
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NO_CONVERSATION.increment()
            log.info("Received dialogsvar, but no existing conversation found: msgId ${kafkaForesporselSvarFraBehandler.msgId}")
        }
    }

    private fun storeForesporselSvar(
        connection: Connection,
        kafkaForesporselSvarFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
    ) {
        val meldingId = connection.createMeldingFraBehandler(
            meldingFraBehandler = kafkaForesporselSvarFraBehandler.toMeldingFraBehandler(),
            fellesformat = kafkaForesporselSvarFraBehandler.fellesformatXML,
            commit = false,
        )
        if (kafkaForesporselSvarFraBehandler.antallVedlegg > 0) {
            val vedlegg = mutableListOf<VedleggDTO>()
            runBlocking {
                vedlegg.addAll(
                    padm2Client.hentVedlegg(kafkaForesporselSvarFraBehandler.msgId)
                )
            }
            vedlegg.forEachIndexed { index, vedleggDTO ->
                connection.createVedlegg(
                    pdf = vedleggDTO.bytes,
                    meldingId = meldingId,
                    number = index,
                    commit = false,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaDialogmeldingFraBehandlerConsumer::class.java)
    }
}
