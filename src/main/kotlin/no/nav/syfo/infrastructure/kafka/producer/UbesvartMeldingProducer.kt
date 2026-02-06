package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.domain.Melding
import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class UbesvartMeldingProducer(
    private val producer: KafkaProducer<String, KafkaMeldingDTO>,
) {
    fun sendUbesvartMelding(
        meldingTilBehandler: Melding.MeldingTilBehandler,
        key: UUID,
    ) =
        try {
            producer.send(
                ProducerRecord(
                    UBESVART_MELDING_TOPIC,
                    key.toString(),
                    KafkaMeldingDTO.from(meldingTilBehandler),
                )
            ).also { it.get() }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send ubesvart melding with key {}: ${e.message}",
                key,
                e
            )
            throw e
        }

    companion object {
        private const val UBESVART_MELDING_TOPIC = "teamsykefravr.ubesvart-melding"
        private val log = LoggerFactory.getLogger(UbesvartMeldingProducer::class.java)
    }
}
