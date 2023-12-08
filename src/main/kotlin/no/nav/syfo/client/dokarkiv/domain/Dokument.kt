package no.nav.syfo.client.dokarkiv.domain

enum class BrevkodeType(
    val value: String,
) {
    FORESPORSEL_OM_PASIENT("OPPF_FORESP_OM_PAS"),
    FORESPORSEL_OM_PASIENT_PAMINNELSE("OPPF_FORESP_OM_PAS_PAM"),
    HENVENDELSE_RETUR_LEGEERKLARING("OPPF_HENV_RETUR_LEGEERKLARING"),
    HENVENDELSE_MELDING_FRA_NAV("OPPF_HENV_MELDING_FRA_NAV"),
    HENVENDELSE_MELDING_TIL_NAV("OPPF_HENV_MELDING_TIL_NAV"),
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
            dokumentvarianter: List<Dokumentvariant>,
            tittel: String? = null,
        ) = Dokument(
            brevkode = brevkode.value,
            dokumentvarianter = dokumentvarianter,
            tittel = tittel,
        )
    }
}
