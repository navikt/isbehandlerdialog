package no.nav.syfo.domain

import java.time.LocalDate

data class Oppfolgingstilfelle(
    val start: LocalDate,
    val end: LocalDate,
) {

    fun isActive(): Boolean =
        LocalDate.now().isBefore(this.end.plusDays(ARBEIDSGIVERPERIODE_DAYS))

    companion object {
        private const val ARBEIDSGIVERPERIODE_DAYS = 16L
    }
}
