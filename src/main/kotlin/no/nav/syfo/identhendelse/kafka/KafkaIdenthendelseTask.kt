package no.nav.syfo.identhendelse.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.kafka.kafkaConsumerConfig
import no.nav.syfo.application.kafka.launchKafkaTask
import no.nav.syfo.identhendelse.IdenthendelseService

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"

fun launchKafkaTaskIdenthendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
) {
    val identhendelseService = IdenthendelseService(
        database = database,
    )

    val kafkaIdenthendelseConsumerService = IdenthendelseConsumerService(
        identhendelseService = identhendelseService,
    )

    launchKafkaTask(
        applicationState = applicationState,
        topic = PDL_AKTOR_TOPIC,
        consumerProperties = kafkaConsumerConfig<KafkaAvroDeserializer>(kafkaEnvironment = kafkaEnvironment),
        kafkaConsumerService = kafkaIdenthendelseConsumerService,
    )
}
