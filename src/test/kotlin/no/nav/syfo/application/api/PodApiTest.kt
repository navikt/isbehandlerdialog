package no.nav.syfo.application.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.api.endpoints.registerPodApi
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.TestDatabaseNotResponding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

class PodApiTest {

    private val database = ExternalMockEnvironment.instance.database
    private fun ApplicationTestBuilder.setupPodApi(database: DatabaseInterface, applicationState: ApplicationState) {
        application {
            routing {
                registerPodApi(
                    applicationState = applicationState,
                    database = database,
                )
            }
        }
    }

    @Nested
    @DisplayName("Successful liveness and readiness checks")
    inner class SuccessfulLivenessAndReadinessChecks {

        @Test
        fun `Returns ok on is_alive`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `Returns ok on is_ready`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_ready")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }
    }

    @Nested
    @DisplayName("Unsuccessful liveness and readiness checks")
    inner class UnsuccessfulLivenessAndReadinessChecks {

        @Test
        fun `Returns internal server error when liveness check fails`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = false, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `Returns internal server error when readiness check fails`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = false),
                )
                val response = client.get("/internal/is_ready")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }
    }

    @Nested
    @DisplayName("Successful liveness and unsuccessful readiness checks when database not working")
    inner class SuccessfulLivenessAndUnsuccessfulReadinessChecksWhenDatabaseNotWorking {
        private val database = TestDatabaseNotResponding()

        @Test
        fun `Returns ok on is_alive`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `Returns internal server error when readiness check fails`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_ready")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }
    }
}
