package no.nav.syfo.melding.domain

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
    val value: Int
) {
    FORESPORSEL(1),
    PAMINNELSE_FORESPORSEL(2),
    SVAR_FORESPORSEL(5),
}
