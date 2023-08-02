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
    FORESPORSEL(1),
    PAMINNELSE_FORESPORSEL(2),
    RETUR_LEGEERKLARING(3),
    SVAR_FORESPORSEL(5),
}
