package no.nav.syfo.melding.kafka.legeerklaring

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_LEGEERKLARING_BASE = "${METRICS_NS}_kafka_legeerklaring"
const val KAFKA_CONSUMER_LEGEERKLARING_READ = "${KAFKA_CONSUMER_LEGEERKLARING_BASE}_read"
const val KAFKA_CONSUMER_LEGEERKLARING_TOMBSTONE = "${KAFKA_CONSUMER_LEGEERKLARING_BASE}_tombstone"
const val KAFKA_CONSUMER_LEGEERKLARING_WITH_CONVREF_STORED = "${KAFKA_CONSUMER_LEGEERKLARING_BASE}_with_convref_stored"
const val KAFKA_CONSUMER_LEGEERKLARING_WITHOUT_CONVREF_STORED =
    "${KAFKA_CONSUMER_LEGEERKLARING_BASE}_without_convref_stored"

val COUNT_KAFKA_CONSUMER_LEGEERKLARING_READ: Counter = Counter.builder(KAFKA_CONSUMER_LEGEERKLARING_READ)
    .description("Counts the number of reads from topic - $LEGEERKLARING_TOPIC")
    .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_LEGEERKLARING_TOMBSTONE: Counter = Counter.builder(KAFKA_CONSUMER_LEGEERKLARING_TOMBSTONE)
    .description("Counts the number of tombstones from topic - $LEGEERKLARING_TOPIC")
    .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_LEGEERKLARING_WITH_CONVREF_STORED: Counter =
    Counter.builder(KAFKA_CONSUMER_LEGEERKLARING_WITH_CONVREF_STORED)
        .description("Counts the number of legeerklaring with conversation ref stored from topic - $LEGEERKLARING_TOPIC")
        .register(METRICS_REGISTRY)

val COUNT_KAFKA_CONSUMER_LEGEERKLARING_WITHOUT_CONVREF_STORED: Counter =
    Counter.builder(KAFKA_CONSUMER_LEGEERKLARING_WITHOUT_CONVREF_STORED)
        .description(
            "Counts the number of legeerklaring without conversation ref stored from topic - $LEGEERKLARING_TOPIC"
        )
        .register(METRICS_REGISTRY)
