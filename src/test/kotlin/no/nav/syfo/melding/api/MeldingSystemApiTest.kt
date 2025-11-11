package no.nav.syfo.melding.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.api.endpoints.meldingerSystemApiBasePath
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

class MeldingSystemApiTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Happy paths")
    inner class HappyPaths {

        private val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            azp = padm2ClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        )

        @Test
        fun `returns no content if melding with msgId doesn't exist`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get("$meldingerSystemApiBasePath/${UUID.randomUUID()}") {
                    bearerAuth(validToken)
                }

                assertEquals(HttpStatusCode.NoContent, response.status)
            }
        }

        @Test
        fun `returns OK if melding with msgId exists`() {
            val msgId = UUID.randomUUID()
            database.createMeldingerFraBehandler(meldingFraBehandler = generateMeldingFraBehandler(msgId = msgId))

            testApplication {
                val client = setupApiAndClient()
                val response = client.get("$meldingerSystemApiBasePath/$msgId") {
                    bearerAuth(validToken)
                }

                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
    }

    @Nested
    @DisplayName("Unhappy paths")
    inner class UnhappyPaths {

        private val apiUrl = "$meldingerSystemApiBasePath/${UUID.randomUUID()}"

        @Test
        fun `should return status Unauthorized if no token is supplied`() {
            testMissingToken(apiUrl, HttpMethod.Get)
        }

        @Test
        fun `should return status Forbidden if wrong azp`() {
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

                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
