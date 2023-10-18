package no.nav.syfo.melding.api

import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class MeldingSystemApiSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
            dialogmeldingBestillingProducer = mockk()
        )

        afterEachTest {
            database.dropData()
        }

        describe("Get melding by msgId") {
            describe("Happy paths") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    azp = padm2ClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                it("returns no content if melding with msgId doesn't exist") {
                    with(
                        handleRequest(HttpMethod.Get, "$meldingerSystemApiBasePath/${UUID.randomUUID()}") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.NoContent
                    }
                }
                it("returns OK if melding with msgId exists") {
                    val msgId = UUID.randomUUID()
                    database.createMeldingerFraBehandler(meldingFraBehandler = generateMeldingFraBehandler(msgId = msgId))

                    with(
                        handleRequest(HttpMethod.Get, "$meldingerSystemApiBasePath/$msgId") {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                }
            }
            describe("Unhappy paths") {
                val apiUrl = "$meldingerSystemApiBasePath/${UUID.randomUUID()}"

                it("should return status Unauthorized if no token is supplied") {
                    with(
                        handleRequest(HttpMethod.Get, apiUrl) {}
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
                it("should return status Forbidden if wrong azp") {
                    val invalidToken = generateJWT(
                        audience = externalMockEnvironment.environment.azure.appClientId,
                        azp = "invalid",
                        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                    )
                    with(
                        handleRequest(HttpMethod.Get, apiUrl) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(invalidToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
        }
    }
})
