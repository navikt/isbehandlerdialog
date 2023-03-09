package no.nav.syfo.melding.kafka.domain

import no.nav.syfo.melding.domain.MeldingType

enum class DialogmeldingType {
    DIALOG_FORESPORSEL,
    DIALOG_NOTAT,
    DIALOG_SVAR,
}

fun DialogmeldingType.getMeldingType(): MeldingType {
    return when (this) {
        DialogmeldingType.DIALOG_SVAR -> MeldingType.FORESPORSEL_PASIENT
        else -> throw IllegalArgumentException("Cannot get MeldingType for DialogmeldingType $this")
    }
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
    SVAR_FORESPORSEL(5),
}
