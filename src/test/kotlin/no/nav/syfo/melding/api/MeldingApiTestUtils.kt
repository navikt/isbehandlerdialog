package no.nav.syfo.melding.api

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.melding.kafka.producer.DialogmeldingBestillingProducer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.testApiModule
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.amshove.kluent.shouldBeEqualTo

fun ApplicationTestBuilder.setupApiAndClient(dialogmeldingBestillingProducer: DialogmeldingBestillingProducer = mockk()): HttpClient {
    application {
        testApiModule(
            externalMockEnvironment = ExternalMockEnvironment.instance,
            dialogmeldingBestillingProducer = dialogmeldingBestillingProducer,
        )
    }
    val client = createClient {
        install(ContentNegotiation) {
            jackson { configure() }
        }
    }
    return client
}

fun testMissingToken(url: String, httpMethod: HttpMethod) {
    testApplication {
        val client = setupApiAndClient()
        val response = if (httpMethod == HttpMethod.Post) {
            client.post(url) {}
        } else {
            client.get(url) {}
        }
        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }
}

fun testMissingPersonIdent(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    testApplication {
        val client = setupApiAndClient()
        val response = if (httpMethod == HttpMethod.Post) {
            client.post(url) {
                bearerAuth(validToken)
            }
        } else {
            client.get(url) {
                bearerAuth(validToken)
            }
        }
        response.status shouldBeEqualTo HttpStatusCode.BadRequest
    }
}

fun testInvalidPersonIdent(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    testApplication {
        val client = setupApiAndClient()
        val response = if (httpMethod == HttpMethod.Post) {
            client.post(url) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1))
            }
        } else {
            client.get(url) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1))
            }
        }
        response.status shouldBeEqualTo HttpStatusCode.BadRequest
    }
}

fun testDeniedPersonAccess(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    testApplication {
        val client = setupApiAndClient()
        val response = if (httpMethod == HttpMethod.Post) {
            client.post(url) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS.value)
            }
        } else {
            client.get(url) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS.value)
            }
        }
        response.status shouldBeEqualTo HttpStatusCode.Forbidden
    }
}
