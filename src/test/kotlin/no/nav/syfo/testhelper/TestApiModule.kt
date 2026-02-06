package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.kafka.producer.DialogmeldingBestillingProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
) {
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        veilederTilgangskontrollClient = externalMockEnvironment.veilederTilgangskontrollClient,
        meldingService = MeldingService(
            database = externalMockEnvironment.database,
            meldingRepository = externalMockEnvironment.meldingRepository,
            dialogmeldingBestillingProducer = dialogmeldingBestillingProducer,
            pdfgenClient = externalMockEnvironment.pdfgenClient,
        )
    )
}
