package no.nav.syfo.infrastructure.kafka.janitor

data class JanitorEventStatusDTO(
    val eventUUID: String,
    val status: JanitorEventStatus,
)

enum class JanitorEventStatus {
    OK,
    FAILED,
}
