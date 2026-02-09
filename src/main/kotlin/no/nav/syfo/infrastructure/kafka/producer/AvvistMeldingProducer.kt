package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.domain.Melding
import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class AvvistMeldingProducer(val kafkaProducer: KafkaProducer<String, KafkaMeldingDTO>) {

    fun sendAvvistMelding(meldingTilBehandler: Melding.MeldingTilBehandler) {
        val key = UUID.nameUUIDFromBytes(meldingTilBehandler.arbeidstakerPersonIdent.value.toByteArray())
        try {
            kafkaProducer.send(
                ProducerRecord(
                    AVVIST_MELDING_TOPIC,
                    key.toString(),
                    KafkaMeldingDTO.from(meldingTilBehandler)
                )
            ).also { it.get() }
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send avvist melding with key $key: ${e.message}", e)
            throw e
        }
    }

    companion object {
        const val AVVIST_MELDING_TOPIC = "teamsykefravr.avvist-melding"
        private val log = LoggerFactory.getLogger(AvvistMeldingProducer::class.java)
    }
}
