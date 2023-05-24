package no.nav.syfo.melding.status.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.*
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

const val DIALOGMELDING_STATUS_TOPIC = "teamsykefravr.behandler-dialogmelding-status"

fun launchKafkaTaskDialogmeldingStatus(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    meldingService: MeldingService,
) {
    val kafkaDialogmeldingStatusConsumer = KafkaDialogmeldingStatusConsumer(
        database = database,
        meldingService = meldingService,
    )
    val consumerProperties =
        kafkaConsumerConfig<KafkaDialogmeldingStatusDeserializer>(kafkaEnvironment = kafkaEnvironment)

    launchKafkaTask(
        applicationState = applicationState,
        topic = DIALOGMELDING_STATUS_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = kafkaDialogmeldingStatusConsumer,
    )
}

class KafkaDialogmeldingStatusDeserializer : Deserializer<KafkaDialogmeldingStatusDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KafkaDialogmeldingStatusDTO =
        mapper.readValue(data, KafkaDialogmeldingStatusDTO::class.java)
}
