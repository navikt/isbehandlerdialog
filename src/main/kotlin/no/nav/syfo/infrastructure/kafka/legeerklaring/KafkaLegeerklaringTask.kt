package no.nav.syfo.infrastructure.kafka.legeerklaring

import com.google.cloud.storage.StorageOptions
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.config.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.config.kafkaConsumerConfig
import no.nav.syfo.infrastructure.kafka.config.launchKafkaTask
import no.nav.syfo.infrastructure.kafka.domain.KafkaLegeerklaeringMessage
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Deserializer

const val LEGEERKLARING_TOPIC = "teamsykmelding.legeerklaering"

fun launchKafkaTaskLegeerklaring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    bucketName: String,
    bucketNameVedlegg: String,
    database: DatabaseInterface,
    pdfgenClient: PdfGenClient,
) {
    val storage = StorageOptions.newBuilder().build().service
    val kafkaLegeerklaringConsumer = KafkaLegeerklaringConsumer(
        database = database,
        storage = storage,
        bucketName = bucketName,
        bucketNameVedlegg = bucketNameVedlegg,
        pdfgenClient = pdfgenClient,
    )
    val consumerProperties =
        kafkaConsumerConfig<KafkaLegeerklaringDeserializer>(kafkaEnvironment = kafkaEnvironment).apply {
            this[ConsumerConfig.GROUP_ID_CONFIG] = "isbehandlerdialog-v1"
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        }

    launchKafkaTask(
        applicationState = applicationState,
        topic = LEGEERKLARING_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = kafkaLegeerklaringConsumer,
    )
}

class KafkaLegeerklaringDeserializer : Deserializer<KafkaLegeerklaeringMessage> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KafkaLegeerklaeringMessage =
        mapper.readValue(data, KafkaLegeerklaeringMessage::class.java)
}
