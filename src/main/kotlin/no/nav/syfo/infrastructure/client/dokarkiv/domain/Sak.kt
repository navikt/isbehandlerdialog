package no.nav.syfo.infrastructure.client.dokarkiv.domain

enum class SaksType(
    val value: String,
) {
    GENERELL("GENERELL_SAK"),
}

data class Sak(
    val sakstype: String = SaksType.GENERELL.value,
)
