package no.nav.syfo.client.pdfgen

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_PDFGEN_BASE = "${METRICS_NS}_call_dialogmeldingpdfgen"

const val CALL_PDFGEN_SUCCESS = "${CALL_PDFGEN_BASE}_success_count"
const val CALL_PDFGEN_FAIL = "${CALL_PDFGEN_BASE}_fail_count"

val COUNT_CALL_PDFGEN_SUCCESS: Counter = Counter
    .builder(CALL_PDFGEN_SUCCESS)
    .description("Counts the number of successful calls to dialogmeldingpdfgen")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDFGEN_FAIL: Counter = Counter
    .builder(CALL_PDFGEN_FAIL)
    .description("Counts the number of failed calls to dialogmeldingpdfgen")
    .register(METRICS_REGISTRY)
