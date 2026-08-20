package no.nav.syfo.infrastructure.kafka.janitor

data class JanitorEventDTO(
    val referenceUUID: String,
    val navident: String,
    val eventUUID: String,
    val personident: String,
    val action: String,
)

enum class JanitorAction {
    SLETT_BEHANDLERDIALOG,
}
