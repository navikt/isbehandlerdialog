package no.nav.syfo.melding.status.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_DIALOGMELDING_STATUS_BASE = "${METRICS_NS}_kafka_consumer_dialogmelding_status"
const val KAFKA_CONSUMER_DIALOGMELDING_STATUS_READ = "${KAFKA_CONSUMER_DIALOGMELDING_STATUS_BASE}_read"
const val KAFKA_CONSUMER_DIALOGMELDING_STATUS_TOMBSTONE =
    "${KAFKA_CONSUMER_DIALOGMELDING_STATUS_BASE}_tombstone"
const val KAFKA_CONSUMER_DIALOGMELDING_STATUS_CREATED =
    "${KAFKA_CONSUMER_DIALOGMELDING_STATUS_BASE}_created_melding_status"
const val KAFKA_CONSUMER_DIALOGMELDING_STATUS_UPDATED =
    "${KAFKA_CONSUMER_DIALOGMELDING_STATUS_BASE}_updated_melding_status"
const val KAFKA_CONSUMER_DIALOGMELDING_STATUS_SKIPPED =
    "${KAFKA_CONSUMER_DIALOGMELDING_STATUS_BASE}_skipped_no_melding"

val COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_READ: Counter = Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_STATUS_READ)
    .description("Counts the number of reads from topic - teamsykefravr.behandler-dialogmelding-status")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_TOMBSTONE: Counter = Counter.builder(
    KAFKA_CONSUMER_DIALOGMELDING_STATUS_TOMBSTONE
)
    .description("Counts the number of tombstones from topic - teamsykefravr.behandler-dialogmelding-status")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_CREATED: Counter = Counter.builder(
    KAFKA_CONSUMER_DIALOGMELDING_STATUS_CREATED
)
    .description("Counts the number of melding_status created from topic - teamsykefravr.behandler-dialogmelding-status")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_UPDATED: Counter = Counter.builder(
    KAFKA_CONSUMER_DIALOGMELDING_STATUS_UPDATED
)
    .description("Counts the number of melding_status updated from topic - teamsykefravr.behandler-dialogmelding-status")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_STATUS_SKIPPED: Counter = Counter.builder(
    KAFKA_CONSUMER_DIALOGMELDING_STATUS_SKIPPED
)
    .description("Counts the number of status skipped from topic because no melding found - teamsykefravr.behandler-dialogmelding-status")
    .register(METRICS_REGISTRY)
