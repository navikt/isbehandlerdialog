package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.config.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.config.kafkaConsumerConfig
import no.nav.syfo.infrastructure.kafka.config.launchKafkaTask
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.database.repository.MeldingRepository
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

const val DIALOGMELDING_STATUS_TOPIC = "teamsykefravr.behandler-dialogmelding-status"

fun launchKafkaTaskDialogmeldingStatus(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    meldingRepository: MeldingRepository,
    meldingService: MeldingService,
) {
    val dialogmeldingStatusConsumer = DialogmeldingStatusConsumer(
        database = database,
        meldingRepository = meldingRepository,
        meldingService = meldingService,
    )
    val consumerProperties =
        kafkaConsumerConfig<KafkaDialogmeldingStatusDeserializer>(kafkaEnvironment = kafkaEnvironment)

    launchKafkaTask(
        applicationState = applicationState,
        topic = DIALOGMELDING_STATUS_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = dialogmeldingStatusConsumer,
    )
}

class KafkaDialogmeldingStatusDeserializer : Deserializer<KafkaDialogmeldingStatusDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KafkaDialogmeldingStatusDTO =
        mapper.readValue(data, KafkaDialogmeldingStatusDTO::class.java)
}
