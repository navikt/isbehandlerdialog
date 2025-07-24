package no.nav.syfo.testhelper

import io.mockk.mockk
import no.nav.syfo.ApplicationState
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.client.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.repository.MeldingRepository
import no.nav.syfo.testhelper.mock.mockHttpClient
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    val environment = testEnvironment()

    val mockHttpClient = mockHttpClient(environment = environment)

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()

    val meldingRepository = MeldingRepository(database = database)

    val meldingService = MeldingService(
        database = database,
        meldingRepository = MeldingRepository(database),
        dialogmeldingBestillingProducer = mockk(),
        pdfgenClient = mockk(),
    )

    companion object {
        val instance: ExternalMockEnvironment = ExternalMockEnvironment()
    }
}
