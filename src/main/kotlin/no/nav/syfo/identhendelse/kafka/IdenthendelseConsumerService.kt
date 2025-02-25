package no.nav.syfo.identhendelse.kafka

import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.identhendelse.IdenthendelseService
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class IdenthendelseConsumerService(
    private val identhendelseService: IdenthendelseService,
) : KafkaConsumerService<GenericRecord> {
    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, GenericRecord>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            records.forEach { record ->
                if (record.value() != null) {
                    identhendelseService.handleIdenthendelse(record.value().toKafkaIdenthendelseDTO())
                } else {
                    log.warn("Identhendelse: Value of ConsumerRecord from topic $PDL_AKTOR_TOPIC is null, probably due to a tombstone. Contact the owner of the topic if an error is suspected")
                }
            }
            kafkaConsumer.commitSync()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.identhendelse.kafka")
    }
}

fun GenericRecord.toKafkaIdenthendelseDTO(): KafkaIdenthendelseDTO {
    val identifikatorer = (get("identifikatorer") as GenericData.Array<GenericRecord>).map {
        Identifikator(
            idnummer = it.get("idnummer").toString(),
            gjeldende = it.get("gjeldende").toString().toBoolean(),
            type = when (it.get("type").toString()) {
                "FOLKEREGISTERIDENT" -> IdentType.FOLKEREGISTERIDENT
                "AKTORID" -> IdentType.AKTORID
                "NPID" -> IdentType.NPID
                else -> throw IllegalStateException("Har mottatt ident med ukjent type")
            }
        )
    }
    return KafkaIdenthendelseDTO(identifikatorer)
}
