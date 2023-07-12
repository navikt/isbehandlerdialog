package no.nav.syfo.melding.kafka.domain

data class KafkaLegeerklaeringMessage(
    val legeerklaeringObjectId: String,
    val validationResult: ValidationResult,
    val vedlegg: List<String>?
)

data class ValidationResult(
    val status: Status,
)

enum class Status {
    OK,
    INVALID
}
