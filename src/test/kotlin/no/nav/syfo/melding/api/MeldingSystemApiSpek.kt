package no.nav.syfo.melding.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class MeldingSystemApiSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database

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
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$meldingerSystemApiBasePath/${UUID.randomUUID()}") {
                        bearerAuth(validToken)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.NoContent
                }
            }
            it("returns OK if melding with msgId exists") {
                val msgId = UUID.randomUUID()
                database.createMeldingerFraBehandler(meldingFraBehandler = generateMeldingFraBehandler(msgId = msgId))

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$meldingerSystemApiBasePath/$msgId") {
                        bearerAuth(validToken)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK
                }
            }
        }
        describe("Unhappy paths") {
            val apiUrl = "$meldingerSystemApiBasePath/${UUID.randomUUID()}"

            it("should return status Unauthorized if no token is supplied") {
                testMissingToken(apiUrl, HttpMethod.Get)
            }
            it("should return status Forbidden if wrong azp") {
                val invalidToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    azp = "invalid",
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(apiUrl) {
                        bearerAuth(invalidToken)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                }
            }
        }
    }
})
