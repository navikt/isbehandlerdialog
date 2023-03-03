package no.nav.syfo.melding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.melding.database.createMeldingFraBehandler
import no.nav.syfo.melding.kafka.config.kafkaDialogmeldingFraBehandlerConsumerConfig
import no.nav.syfo.melding.kafka.domain.KafkaDialogmeldingFraBehandlerDTO
import no.nav.syfo.melding.kafka.domain.toMeldingFraBehandler
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

fun pollAndProcessDialogmeldingFraBehandler(
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

fun processConsumerRecords(
    consumerRecords: ConsumerRecords<String, KafkaDialogmeldingFraBehandlerDTO>,
    database: DatabaseInterface,
) {
    database.connection.use { connection ->
        consumerRecords.forEach {
            val kafkaDialogmeldingFraBehandler = it.value()
            log.info("Received a dialogmelding from behandler: navLogId: ${kafkaDialogmeldingFraBehandler.navLogId}, kontorOrgnr: ${kafkaDialogmeldingFraBehandler.legekontorOrgNr}, msgId: ${kafkaDialogmeldingFraBehandler.msgId}")
            // TODO: Filtrere ut de meldingene vi faktisk skal lagre
            connection.createMeldingFraBehandler(
                meldingFraBehandler = kafkaDialogmeldingFraBehandler.toMeldingFraBehandler(),
                fellesformat = kafkaDialogmeldingFraBehandler.fellesformatXML,
            )
        }
        connection.commit()
    }
}
