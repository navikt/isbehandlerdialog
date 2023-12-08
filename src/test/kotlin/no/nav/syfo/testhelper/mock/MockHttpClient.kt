package no.nav.syfo.testhelper.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.syfo.application.Environment
import no.nav.syfo.client.commonConfig

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.azure.openidConfigTokenEndpoint}" -> azureAdMockResponse()
                requestUrl.startsWith("/${environment.clients.istilgangskontroll.baseUrl}") -> tilgangskontrollMockResponse(
                    request
                )

                requestUrl.startsWith("/${environment.clients.dialogmeldingpdfgen.baseUrl}") -> pdfGenClientMockResponse(
                    request
                )

                requestUrl.startsWith("/${environment.clients.padm2.baseUrl}") -> padm2ClientMockResponse(request)

                requestUrl.startsWith("/${environment.clients.oppfolgingstilfelle.baseUrl}") -> oppfolgingstilfelleClientMockResponse(request)

                requestUrl.startsWith("/${environment.clients.dokarkiv.baseUrl}") -> dokarkivMockResponse(request)

                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
