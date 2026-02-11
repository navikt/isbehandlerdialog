package no.nav.syfo.melding.api

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.infrastructure.kafka.producer.DialogmeldingBestillingProducer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.testApiModule
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import kotlin.test.assertEquals

fun ApplicationTestBuilder.setupApiAndClient(dialogmeldingBestillingProducer: DialogmeldingBestillingProducer = mockk()): HttpClient {
    application {
        testApiModule(externalMockEnvironment = ExternalMockEnvironment.instance, dialogmeldingBestillingProducer)
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
        assertEquals(HttpStatusCode.Unauthorized, response.status)
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
        assertEquals(HttpStatusCode.BadRequest, response.status)
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
        assertEquals(HttpStatusCode.BadRequest, response.status)
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
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
