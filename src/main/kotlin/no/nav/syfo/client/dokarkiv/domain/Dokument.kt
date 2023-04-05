package no.nav.syfo.client.dokarkiv.domain

enum class BrevkodeType(
    val value: String,
) {
    // TODO: Add brevkodetyper for Forespørsel om pasient
}

data class Dokument private constructor(
    val brevkode: String,
    val dokumentKategori: String? = null,
    val dokumentvarianter: List<Dokumentvariant>,
    val tittel: String? = null,
) {
    companion object {
        fun create(
            brevkode: BrevkodeType,
            dokumentKategori: String? = null,
            dokumentvarianter: List<Dokumentvariant>,
            tittel: String? = null,
        ) = Dokument(
            brevkode = brevkode.value,
            dokumentKategori = dokumentKategori,
            dokumentvarianter = dokumentvarianter,
            tittel = tittel,
        )
    }
}
