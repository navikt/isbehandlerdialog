package no.nav.syfo.client.oppfolgingstilfelle

import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.toLatestOppfolgingstilfelle
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OppfolgingstilfellePersonDTOTest {

    @Test
    fun `Return tilfelle with latest end date if more than one have the same latest start date`() {
        val startDate = LocalDate.now().minusDays(10)
        val earliestEndDate = LocalDate.now().minusDays(5)
        val latestEndDate = LocalDate.now().plusDays(5)

        val tilfelleWithWrongEndDate = oppfolgingstilfelleDTO(
            startDate = startDate,
            endDate = earliestEndDate,
        )
        val tilfelleWithCorrectEndDate = oppfolgingstilfelleDTO(
            startDate = startDate,
            endDate = latestEndDate,
        )
        val oppfolgingstilfellePersonDTO = OppfolgingstilfellePersonDTO(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT.value,
            oppfolgingstilfelleList = listOf(
                tilfelleWithWrongEndDate,
                tilfelleWithCorrectEndDate,
            ),
        )

        val latestTilfelle = oppfolgingstilfellePersonDTO.toLatestOppfolgingstilfelle()

        assertEquals(startDate, latestTilfelle?.start)
        assertEquals(latestEndDate, latestTilfelle?.end)
    }
}

fun oppfolgingstilfelleDTO(
    startDate: LocalDate,
    endDate: LocalDate,
): OppfolgingstilfelleDTO {
    return OppfolgingstilfelleDTO(
        arbeidstakerAtTilfelleEnd = true,
        start = startDate,
        end = endDate,
        virksomhetsnummerList = listOf(
            UserConstants.VIRKSOMHETSNUMMER
        ),
    )
}
