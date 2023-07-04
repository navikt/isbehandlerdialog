package no.nav.syfo.melding.kafka.legeerklaring

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.*
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Deserializer

const val LEGEERKLARING_TOPIC = "teamsykmelding.legeerklaering"

fun launchKafkaTaskLegeerklaring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    meldingService: MeldingService,
) {
    val kafkaLegeerklaringConsumer = KafkaLegeerklaringConsumer(
        database = database,
        meldingService = meldingService,
    )
    val consumerProperties =
        kafkaConsumerConfig<KafkaLegeerklaringDeserializer>(kafkaEnvironment = kafkaEnvironment).apply {
            this[ConsumerConfig.GROUP_ID_CONFIG] = "isbehandlerdialog-v0"
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }

    launchKafkaTask(
        applicationState = applicationState,
        topic = LEGEERKLARING_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = kafkaLegeerklaringConsumer,
    )
}

class KafkaLegeerklaringDeserializer : Deserializer<KafkaLegeerklaringDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KafkaLegeerklaringDTO =
        mapper.readValue(data, KafkaLegeerklaringDTO::class.java)
}
