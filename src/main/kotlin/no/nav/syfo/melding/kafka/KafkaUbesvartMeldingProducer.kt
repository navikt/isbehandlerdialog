package no.nav.syfo.melding.kafka

import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.toKafkaUbesvartMeldingDTO
import no.nav.syfo.melding.kafka.domain.KafkaUbesvartMeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class KafkaUbesvartMeldingProducer(
    private val ubesvartMeldingKafkaProducer: KafkaProducer<String, KafkaUbesvartMeldingDTO>,
) {
    fun sendUbesvartMelding(
        meldingTilBehandler: MeldingTilBehandler,
        key: UUID,
    ) {
        try {
            ubesvartMeldingKafkaProducer.send(
                ProducerRecord(
                    UBESVART_MELDING_TOPIC,
                    key.toString(),
                    meldingTilBehandler.toKafkaUbesvartMeldingDTO(),
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send ubesvart melding with key {}: ${e.message}",
                key,
                e
            )
            throw e
        }
    }

    companion object {
        const val UBESVART_MELDING_TOPIC = "teamsykefravr.ubesvart-melding"
        private val log = LoggerFactory.getLogger(KafkaUbesvartMeldingProducer::class.java)
    }
}
