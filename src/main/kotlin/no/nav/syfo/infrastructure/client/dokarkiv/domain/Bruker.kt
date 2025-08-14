package no.nav.syfo.infrastructure.client.dokarkiv.domain

enum class BrukerIdType(
    val value: String,
) {
    HPRNR("HPRNR"),
    PERSON_IDENT("FNR"),
    VIRKSOMHETSNUMMER("ORGNR"),
}

data class Bruker private constructor(
    val id: String,
    val idType: String,
) {
    companion object {
        fun create(
            id: String,
            idType: BrukerIdType,
        ) = Bruker(
            id = id,
            idType = idType.value
        )
    }
}
