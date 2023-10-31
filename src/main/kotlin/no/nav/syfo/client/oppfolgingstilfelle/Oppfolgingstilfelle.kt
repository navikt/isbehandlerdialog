package no.nav.syfo.client.oppfolgingstilfelle

import java.time.LocalDate

const val ARBEIDSGIVERPERIODE_DAYS = 16L

data class Oppfolgingstilfelle(
    val start: LocalDate,
    val end: LocalDate,
)

fun Oppfolgingstilfelle.isActive(): Boolean = !isInactive()

fun Oppfolgingstilfelle.isInactive(): Boolean =
    LocalDate.now().isAfter(this.end.plusDays(ARBEIDSGIVERPERIODE_DAYS))
