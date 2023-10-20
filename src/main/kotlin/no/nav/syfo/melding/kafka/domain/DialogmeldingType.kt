package no.nav.syfo.melding.kafka.domain

enum class DialogmeldingType {
    DIALOG_FORESPORSEL,
    DIALOG_NOTAT,
    DIALOG_SVAR,
}

enum class DialogmeldingKodeverk {
    DIALOGMOTE,
    HENVENDELSE,
    FORESPORSEL,
}

enum class DialogmeldingKode(
    val value: Int,
) {
    RETUR_LEGEERKLARING(3), // kodeverk 8127
    MELDING_FRA_NAV(8), // kodeverk 8127
    HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING(1), // kodeverk 8128
    FORESPORSEL(1), // kodeverk 8129
    PAMINNELSE_FORESPORSEL(2), // kodeverk 8129
    SVAR_FORESPORSEL(5), // kodeverk 9069
}
