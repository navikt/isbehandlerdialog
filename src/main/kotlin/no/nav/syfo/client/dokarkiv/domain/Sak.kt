package no.nav.syfo.client.dokarkiv.domain

enum class SaksType(
    val value: String,
) {
    GENERELL("GENERELL_SAK"),
}

data class Sak private constructor(
    val sakstype: String,
) {
    companion object {
        operator fun invoke(
            sakstype: SaksType,
        ) = Sak(
            sakstype = sakstype.value,
        )
    }
}
