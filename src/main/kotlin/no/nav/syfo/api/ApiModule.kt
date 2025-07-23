package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import no.nav.syfo.api.endpoints.registerMeldingApi
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.access.APIConsumerAccessService
import no.nav.syfo.api.auth.JwtIssuer
import no.nav.syfo.api.auth.JwtIssuerType
import no.nav.syfo.api.auth.installJwtAuthentication
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.application.MeldingService
import no.nav.syfo.api.endpoints.registerMeldingSystemApi
import no.nav.syfo.api.endpoints.registerPodApi
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.client.wellknown.WellKnown
import no.nav.syfo.util.METRICS_REGISTRY

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    meldingService: MeldingService,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installStatusPages()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.azure.appClientId),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = wellKnownInternalAzureAD,
            ),
        )
    )
    val apiConsumerAccessService = APIConsumerAccessService(
        azureAppPreAuthorizedApps = environment.azure.appPreAuthorizedApps,
    )

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerMeldingApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                meldingService = meldingService,
            )
            registerMeldingSystemApi(
                apiConsumerAccessService = apiConsumerAccessService,
                meldingService = meldingService,
            )
        }
    }
}

fun Routing.registerMetricApi() {
    get("/internal/metrics") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
