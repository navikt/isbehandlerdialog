package no.nav.syfo.client.oppfolgingstilfelle

import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.isBeforeOrEqual
import java.time.LocalDate

data class OppfolgingstilfellePersonDTO(
    val oppfolgingstilfelleList: List<OppfolgingstilfelleDTO>,
    val personIdent: String,
)

data class OppfolgingstilfelleDTO(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<String>,
)

fun OppfolgingstilfellePersonDTO.toLatestOppfolgingstilfelle(): Oppfolgingstilfelle? {
    val latestTilfelleStartDate =
        this.oppfolgingstilfelleList.maxByOrNull { oppfolgingstilfelle -> oppfolgingstilfelle.start }?.start

    val allTilfellerWithLatestStart =
        this.oppfolgingstilfelleList.filter { oppfolgingstilfelle -> oppfolgingstilfelle.start == latestTilfelleStartDate }

    val latestTilfelleWithLatestEnd =
        allTilfellerWithLatestStart.maxByOrNull { oppfolgingstilfelle -> oppfolgingstilfelle.end }

    return latestTilfelleWithLatestEnd?.let { oppfolgingstilfelleDTO ->
        Oppfolgingstilfelle(
            start = oppfolgingstilfelleDTO.start,
            end = oppfolgingstilfelleDTO.end,
        )
    }
}

fun List<OppfolgingstilfelleDTO>.toOppfolgingstilfelle(): List<Oppfolgingstilfelle> {
    return this.map {
        Oppfolgingstilfelle(
            start = it.start,
            end = it.end
        )
    }
}

fun List<Oppfolgingstilfelle>.findOppfolgingstilfelleByDate(date: LocalDate): Oppfolgingstilfelle? {
    val oppfolgingstilfeller = this.filter {
        it.start.isBeforeOrEqual(date) && it.end.isAfterOrEqual(date)
    }

    return oppfolgingstilfeller.minByOrNull { it.start }
}
