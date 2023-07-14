package no.nav.syfo.melding.kafka.producer

import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.toKafkaMeldingDTO
import no.nav.syfo.melding.kafka.domain.KafkaMeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class AvvistMeldingProducer(producerConfig: Properties) :
    KafkaProducer<String, KafkaMeldingDTO>(producerConfig) {

    fun sendAvvistMelding(meldingTilBehandler: MeldingTilBehandler) {
        val key = UUID.nameUUIDFromBytes(meldingTilBehandler.arbeidstakerPersonIdent.value.toByteArray())
        try {
            send(
                ProducerRecord(
                    AVVIST_MELDING_TOPIC,
                    key.toString(),
                    meldingTilBehandler.toKafkaMeldingDTO(),
                )
            ).get()
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
