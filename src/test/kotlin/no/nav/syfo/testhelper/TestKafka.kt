package no.nav.syfo.testhelper

import no.nav.syfo.melding.kafka.producer.AvvistMeldingProducer
import no.nav.syfo.melding.kafka.producer.AvvistMeldingProducer.Companion.AVVIST_MELDING_TOPIC
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import java.util.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.Environment
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import no.nav.syfo.melding.kafka.config.KafkaMeldingDTOSerializer

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        AVVIST_MELDING_TOPIC,
    ),
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)

fun Properties.overrideForTest(): Properties = apply {
    remove(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)
    remove(SaslConfigs.SASL_MECHANISM)
}

fun testAvvistMeldingProducer(
    environment: Environment,
): AvvistMeldingProducer {
    val avvistMeldingProducerProperties = kafkaAivenProducerConfig<KafkaMeldingDTOSerializer>(environment.kafka)
        .overrideForTest()
    return AvvistMeldingProducer(avvistMeldingProducerProperties)
}

// fun testPersonoppgavehendelseConsumer(
//     environment: Environment,
// ): KafkaConsumer<String, String> {
//     val consumerPropertiesPersonoppgavehendelse = kafkaAivenConsumerConfig<StringDeserializer>(environment.kafka)
//         .overrideForTest()
//         .apply {
//             put("specific.avro.reader", false)
//         }
//     val consumerpersonoppgavehendelse = KafkaConsumer<String, String>(consumerPropertiesPersonoppgavehendelse)
//     consumerpersonoppgavehendelse.subscribe(listOf(PERSONOPPGAVEHENDELSE_TOPIC))
//     return consumerpersonoppgavehendelse
// }
