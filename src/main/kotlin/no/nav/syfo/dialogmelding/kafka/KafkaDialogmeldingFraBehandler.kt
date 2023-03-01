package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.dialogmelding.database.createMelding
import no.nav.syfo.dialogmelding.domain.toPMelding
import no.nav.syfo.dialogmelding.kafka.domain.KafkaDialogmeldingFromBehandlerDTO
import no.nav.syfo.dialogmelding.kafka.domain.toMeldingFraBehandler
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

const val DIALOGMELDING_FROM_BEHANDLER_TOPIC = "teamsykefravr.dialogmelding"

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.dialogmelding.kafka")

fun blockingApplicationLogicDialogmeldingFraBehandler(
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
        pollAndProcessDialogmeldingFraBehandler(
            database = database,
            kafkaConsumerDialogmeldingFromBehandler = kafkaConsumerDialogmelding,
        )
    }
}

fun pollAndProcessDialogmeldingFraBehandler(
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
    database.connection.use { connection ->
        consumerRecords.forEach {
            val kafkaDialogmeldingFraBehandler = it.value()
            log.info("Received a dialogmelding from behandler: navLogId: ${kafkaDialogmeldingFraBehandler.navLogId}, kontorOrgnr: ${kafkaDialogmeldingFraBehandler.legekontorOrgNr}, msgId: ${kafkaDialogmeldingFraBehandler.msgId}")
            // TODO: Filtrere ut de meldingene vi faktisk skal lagre
            connection.createMelding(
                melding = kafkaDialogmeldingFraBehandler.toMeldingFraBehandler().toPMelding(),
                fellesformat = kafkaDialogmeldingFraBehandler.fellesformatXML,
            )
        }
        connection.commit()
    }
}
