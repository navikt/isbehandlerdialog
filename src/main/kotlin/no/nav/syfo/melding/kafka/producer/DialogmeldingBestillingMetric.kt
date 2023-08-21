
package no.nav.syfo.melding.kafka.producer

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.melding.kafka.producer.DialogmeldingBestillingProducer.Companion.BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC

const val KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_BASE = "${METRICS_NS}_kafka_producer_melding_til_behandler_bestilling"
const val KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_SENT =
    "${KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_BASE}_sent"
const val KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_ERROR =
    "${KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_BASE}_error"
const val KAFKA_CONSUMER_PAMINNELSE_BESTILLING_SENT =
    "${KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_BASE}_paminnelse_sent"
const val KAFKA_CONSUMER_FORESPORSEL_TILLEGGSOPPLYSNING_BESTILLING_SENT =
    "${KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_BASE}_foresporsel_tilleggsopplysning_sent"
const val KAFKA_CONSUMER_FORESPORSEL_LEGEERKLARING_BESTILLING_SENT =
    "${KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_BASE}_foresporsel_legeerklaring_sent"
const val KAFKA_CONSUMER_RETUR_LEGEERKLARING_BESTILLING_SENT =
    "${KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_BASE}_retur_legeerklaring_sent"

val COUNT_KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_SENT: Counter =
    Counter.builder(KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_SENT)
        .description("Counts the number of melding til behandler bestillinger sent to topic - $BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_ERROR: Counter =
    Counter.builder(KAFKA_CONSUMER_MELDING_TIL_BEHANDLER_BESTILLING_ERROR)
        .description("Counts the number of melding til behandler bestillinger that failed with error to topic - $BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_PAMINNELSE_BESTILLING_SENT: Counter =
    Counter.builder(KAFKA_CONSUMER_PAMINNELSE_BESTILLING_SENT)
        .description("Counts the number of paminnelse bestillinger sent to topic - $BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_FORESPORSEL_TILLEGGSOPPLYSNING_BESTILLING_SENT: Counter =
    Counter.builder(KAFKA_CONSUMER_FORESPORSEL_TILLEGGSOPPLYSNING_BESTILLING_SENT)
        .description("Counts the number of foresporsel tilleggsopplysninger bestillinger sent to topic - $BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_FORESPORSEL_LEGEERKLARING_BESTILLING_SENT: Counter =
    Counter.builder(KAFKA_CONSUMER_FORESPORSEL_LEGEERKLARING_BESTILLING_SENT)
        .description("Counts the number of foresporsel legeerklaring bestillinger sent to topic - $BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_RETUR_LEGEERKLARING_BESTILLING_SENT: Counter =
    Counter.builder(KAFKA_CONSUMER_RETUR_LEGEERKLARING_BESTILLING_SENT)
        .description("Counts the number of retur av legeerklaring bestillinger sent to topic - $BEHANDLER_DIALOGMELDING_BESTILLING_TOPIC")
        .register(METRICS_REGISTRY)
