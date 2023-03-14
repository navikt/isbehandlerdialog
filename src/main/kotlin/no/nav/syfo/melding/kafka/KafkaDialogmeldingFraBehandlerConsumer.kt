package no.nav.syfo.melding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
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
) {
    val consumerProperties = kafkaDialogmeldingFraBehandlerConsumerConfig(kafkaEnvironment)
    val kafkaConsumerDialogmelding = KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>(consumerProperties)
    kafkaConsumerDialogmelding.subscribe(
        listOf(DIALOGMELDING_FROM_BEHANDLER_TOPIC)
    )
    while (applicationState.ready) {
        pollAndProcessDialogmeldingFraBehandler(
            database = database,
            kafkaConsumerDialogmeldingFraBehandler = kafkaConsumerDialogmelding,
        )
    }
}

internal fun pollAndProcessDialogmeldingFraBehandler(
    database: DatabaseInterface,
    kafkaConsumerDialogmeldingFraBehandler: KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>,
) {
    val records = kafkaConsumerDialogmeldingFraBehandler.poll(Duration.ofMillis(1000))
    if (records.count() > 0) {
        processConsumerRecords(
            consumerRecords = records,
            database = database,
        )
        kafkaConsumerDialogmeldingFraBehandler.commitSync()
    }
}

internal fun processConsumerRecords(
    consumerRecords: ConsumerRecords<String, KafkaDialogmeldingFraBehandlerDTO>,
    database: DatabaseInterface,
) {
    database.connection.use { connection ->
        consumerRecords.forEach {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ.increment()
            val kafkaDialogmeldingFraBehandler = it.value()
            if (kafkaDialogmeldingFraBehandler != null) {
                handleIncomingMessage(kafkaDialogmeldingFraBehandler, connection)
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
) {
    if (kafkaDialogmeldingFraBehandler.isForesporselSvarWithConversationRef()) {
        handleForesporselSvar(kafkaDialogmeldingFraBehandler, connection)
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
) {
    val conversationRef = kafkaForesporselSvarFraBehandler.conversationRef!!
    if (
        connection.hasSendtMeldingForConversationRefAndArbeidstakerIdent(
            conversationRef = conversationRef,
            arbeidstakerPersonIdent = PersonIdent(kafkaForesporselSvarFraBehandler.personIdentPasient),
        )
    ) {
        if (connection.getMelding(UUID.fromString(kafkaForesporselSvarFraBehandler.msgId)) == null) {
            log.info("Received a dialogmelding from behandler: $conversationRef")
            connection.createMeldingFraBehandler(
                meldingFraBehandler = kafkaForesporselSvarFraBehandler.toMeldingFraBehandler(),
                fellesformat = kafkaForesporselSvarFraBehandler.fellesformatXML,
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
