package no.nav.syfo.melding.kafka

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.client.padm2.VedleggDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.kafka.config.kafkaDialogmeldingFraBehandlerConsumerConfig
import no.nav.syfo.melding.kafka.domain.*
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.UUID

const val DIALOGMELDING_FROM_BEHANDLER_TOPIC = "teamsykefravr.dialogmelding"

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.melding.kafka")

fun blockingApplicationLogicDialogmeldingFraBehandler(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    padm2Client: Padm2Client,
) {
    val consumerProperties = kafkaDialogmeldingFraBehandlerConsumerConfig(kafkaEnvironment)
    val kafkaConsumerDialogmelding = KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>(consumerProperties)
    kafkaConsumerDialogmelding.subscribe(
        listOf(DIALOGMELDING_FROM_BEHANDLER_TOPIC)
    )
    while (applicationState.ready) {
        pollAndProcessDialogmeldingFraBehandler(
            database = database,
            padm2Client = padm2Client,
            kafkaConsumerDialogmeldingFraBehandler = kafkaConsumerDialogmelding,
        )
    }
}

internal fun pollAndProcessDialogmeldingFraBehandler(
    database: DatabaseInterface,
    padm2Client: Padm2Client,
    kafkaConsumerDialogmeldingFraBehandler: KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>,
) {
    val records = kafkaConsumerDialogmeldingFraBehandler.poll(Duration.ofMillis(1000))
    if (records.count() > 0) {
        processConsumerRecords(
            consumerRecords = records,
            database = database,
            padm2Client = padm2Client,
        )
        kafkaConsumerDialogmeldingFraBehandler.commitSync()
    }
}

internal fun processConsumerRecords(
    consumerRecords: ConsumerRecords<String, KafkaDialogmeldingFraBehandlerDTO>,
    database: DatabaseInterface,
    padm2Client: Padm2Client,
) {
    database.connection.use { connection ->
        consumerRecords.forEach {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ.increment()
            val kafkaDialogmeldingFraBehandler = it.value()
            if (kafkaDialogmeldingFraBehandler != null) {
                handleIncomingMessage(
                    kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                    connection = connection,
                    padm2Client = padm2Client,
                )
            } else {
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_TOMBSTONE.increment()
                log.warn("Received kafkaDialogmeldingFraBehandler with no value: could be tombstone")
            }
        }
        connection.commit()
    }
}

internal fun handleIncomingMessage(
    kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
    connection: Connection,
    padm2Client: Padm2Client,
) {
    if (kafkaDialogmeldingFraBehandler.isForesporselSvarWithConversationRef()) {
        handleForesporselSvar(
            kafkaForesporselSvarFraBehandler = kafkaDialogmeldingFraBehandler,
            connection = connection,
            padm2Client = padm2Client,
        )
    } else if (kafkaDialogmeldingFraBehandler.isForesporselSvarWithoutConversationRef()) {
        log.warn("Received DIALOG_SVAR with missing conversationRef: msgId = ${kafkaDialogmeldingFraBehandler.msgId}")
        COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_CONVERSATION_REF_MISSING.increment()
    } else {
        COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FORESPORSELSVAR.increment()
    }
}

internal fun handleForesporselSvar(
    kafkaForesporselSvarFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
    connection: Connection,
    padm2Client: Padm2Client,
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
                padm2Client = padm2Client,
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
    padm2Client: Padm2Client,
) {
    val meldingId = connection.createMeldingFraBehandler(
        meldingFraBehandler = kafkaForesporselSvarFraBehandler.toMeldingFraBehandler(),
        fellesformat = kafkaForesporselSvarFraBehandler.fellesformatXML,
    )
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
