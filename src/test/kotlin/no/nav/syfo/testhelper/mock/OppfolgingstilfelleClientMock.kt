package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleDTO
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfellePersonDTO
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import java.time.LocalDate

fun MockRequestHandleScope.oppfolgingstilfelleClientMockResponse(request: HttpRequestData): HttpResponseData {
    val requestPersonIdent = request.headers[NAV_PERSONIDENT_HEADER]
    return when (requestPersonIdent) {
        null -> respond(HttpStatusCode.BadRequest)
        UserConstants.ARBEIDSTAKER_PERSONIDENT.value -> respond(
            OppfolgingstilfellePersonDTO(
                oppfolgingstilfelleList = listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusDays(28),
                        end = LocalDate.now(),
                        virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER),
                    ),
                ),
                personIdent = requestPersonIdent
            )
        )
        UserConstants.ARBEIDSTAKER_PERSONIDENT_INACTIVE.value -> respond(
            OppfolgingstilfellePersonDTO(
                oppfolgingstilfelleList = listOf(
                    OppfolgingstilfelleDTO(
                        arbeidstakerAtTilfelleEnd = true,
                        start = LocalDate.now().minusDays(56),
                        end = LocalDate.now().minusDays(20),
                        virksomhetsnummerList = listOf(UserConstants.VIRKSOMHETSNUMMER),
                    ),
                ),
                personIdent = requestPersonIdent
            )
        )
        else -> respond(
            OppfolgingstilfellePersonDTO(emptyList(), requestPersonIdent)
        )
    }
}
