package no.nav.syfo.melding.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE = "${METRICS_NS}_kafka_consumer_dialogmelding_fra_behandler"
const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ = "${KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE}_read"
const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_TOMBSTONE =
    "${KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE}_tombstone"
const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_MELDING_CREATED =
    "${KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE}_created_melding"
const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FORESPORSELSVAR =
    "${KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE}_skipped_not_dialogsvar"
const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NO_CONVERSATION =
    "${KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE}_skipped_no_conversation"
const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_CONVERSATION_REF_MISSING =
    "${KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE}_skipped_conversation_ref_missing"
const val KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_DUPLICATE =
    "${KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_BASE}_skipped_duplicate"

val COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ)
        .description("Counts the number of reads from topic - teamsykefravr.dialogmelding")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_TOMBSTONE: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_TOMBSTONE)
        .description("Counts the number of tombstones received from topic - teamsykefravr.dialogmelding")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_MELDING_CREATED: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_MELDING_CREATED)
        .description("Counts the number of melding created from topic - teamsykefravr.dialogmelding")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FORESPORSELSVAR: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FORESPORSELSVAR)
        .description("Counts the number of melding skipped from topic because not DIALOG_SVAR - teamsykefravr.dialogmelding")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NO_CONVERSATION: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NO_CONVERSATION)
        .description("Counts the number of melding skipped from topic because no conversation found - teamsykefravr.dialogmelding")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_CONVERSATION_REF_MISSING: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_CONVERSATION_REF_MISSING)
        .description("Counts the number of melding skipped from topic because conversationRef is null - teamsykefravr.dialogmelding")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_DUPLICATE: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_DUPLICATE)
        .description("Counts the number of melding skipped from topic because duplicate - teamsykefravr.dialogmelding")
        .register(METRICS_REGISTRY)
