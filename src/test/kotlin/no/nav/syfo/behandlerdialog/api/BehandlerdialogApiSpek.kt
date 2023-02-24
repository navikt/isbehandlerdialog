package no.nav.syfo.behandlerdialog.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BehandlerdialogApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(BehandlerdialogApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )

            val urlGet = behandlerdialogApiBasePath
            val urlPost = "$behandlerdialogApiBasePath$behandlerdialogApiMeldingPath"

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )

            describe("Get melding to behandler for person") {
                describe("Happy path") {
                    it("Returns OK from GET") {
                        with(
                            handleRequest(HttpMethod.Get, urlGet) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlGet, HttpMethod.Get)
                    }
                    it("returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlGet, validToken, HttpMethod.Get)
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlGet, validToken, HttpMethod.Get)
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlGet, validToken, HttpMethod.Get)
                    }
                }
            }

            describe("Create new melding to behandler") {
                val behandlerdialogDTO = generateMeldingTilBehandler()

                it("Will create a new melding to behandler") {
                    with(
                        handleRequest(HttpMethod.Post, urlPost) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            setBody(objectMapper.writeValueAsString(behandlerdialogDTO))
                        }
                    ) {
                        val responseDTO = objectMapper.readValue<MeldingTilBehandlerDTO>(response.content!!)
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        response.call.getPersonIdent() shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT
                        responseDTO.behandlerRef shouldBeEqualTo behandlerdialogDTO.behandlerRef
                        responseDTO.tekst shouldBeEqualTo behandlerdialogDTO.tekst
                    }
                }
            }
        }
    }
})

private fun TestApplicationEngine.testMissingToken(url: String, httpMethod: HttpMethod) {
    with(
        handleRequest(httpMethod, url) {}
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
    }
}

private fun TestApplicationEngine.testMissingPersonIdent(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    with(
        handleRequest(httpMethod, url) {
            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
        }
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
    }
}

private fun TestApplicationEngine.testInvalidPersonIdent(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    with(
        handleRequest(httpMethod, url) {
            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
            addHeader(
                NAV_PERSONIDENT_HEADER,
                UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1)
            )
        }
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
    }
}

private fun TestApplicationEngine.testDeniedPersonAccess(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    with(
        handleRequest(httpMethod, url) {
            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
            addHeader(
                NAV_PERSONIDENT_HEADER,
                UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS.value
            )
        }
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
    }
}
