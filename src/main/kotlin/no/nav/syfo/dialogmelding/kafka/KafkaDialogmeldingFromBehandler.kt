package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

const val DIALOGMELDING_FROM_BEHANDLER_TOPIC = "teamsykefravr.dialogmelding"

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.dialogmelding.kafka")

fun blockingApplicationLogicDialogmeldingFromBehandler(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
) {
    val consumerProperties = kafkaDialogmeldingFromBehandlerConsumerConfig(kafkaEnvironment)
    val kafkaConsumerDialogmelding = KafkaConsumer<String, KafkaDialogmeldingFromBehandlerDTO>(consumerProperties)
    kafkaConsumerDialogmelding.subscribe(
        listOf(DIALOGMELDING_FROM_BEHANDLER_TOPIC)
    )
    while (applicationState.ready) {
        pollAndProcessDialogmeldingFromBehandler(
            database = database,
            kafkaConsumerDialogmeldingFromBehandler = kafkaConsumerDialogmelding,
        )
    }
}

fun pollAndProcessDialogmeldingFromBehandler(
    database: DatabaseInterface,
    kafkaConsumerDialogmeldingFromBehandler: KafkaConsumer<String, KafkaDialogmeldingFromBehandlerDTO>,
) {
    val records = kafkaConsumerDialogmeldingFromBehandler.poll(Duration.ofMillis(1000))
    if (records.count() > 0) {
        processConsumerRecords(
            consumerRecords = records,
            database = database,
        )
        kafkaConsumerDialogmeldingFromBehandler.commitSync()
    }
}

fun processConsumerRecords(
    consumerRecords: ConsumerRecords<String, KafkaDialogmeldingFromBehandlerDTO>,
    database: DatabaseInterface,
) {
    consumerRecords.forEach {
        val dialogmeldingFromBehandler = it.value()
        log.info("Received a dialogmelding from behandler: navLogId: ${dialogmeldingFromBehandler.navLogId}, kontorOrgnr: ${dialogmeldingFromBehandler.legekontorOrgNr}, msgId: ${dialogmeldingFromBehandler.msgId}")
        // TODO: Store incoming dialogmelding in database
    }
}
