package no.nav.syfo.melding.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.melding.database.createMeldingFraBehandler
import no.nav.syfo.melding.database.createMeldingTilBehandler
import no.nav.syfo.melding.database.createVedlegg
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer
import no.nav.syfo.melding.status.database.createMeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatusType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandlerRequestDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class MeldingApiGetSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(MeldingApiGetSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = ExternalMockEnvironment.instance.database
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmeldingBestillingProducer = DialogmeldingBestillingProducer(
                    dialogmeldingBestillingKafkaProducer = mockk(),
                ),
            )

            val apiUrl = meldingApiBasePath

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
            val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
            val veilederIdent = UserConstants.VEILEDER_IDENT

            afterEachTest {
                database.dropData()
            }

            describe("Get meldinger for person") {
                describe("Happy path") {
                    it("Returns all meldinger for personident grouped by conversationRef") {
                        val (firstConversation, _) = database.createMeldingerTilBehandler(
                            meldingTilBehandler = defaultMeldingTilBehandler,
                            numberOfMeldinger = 2,
                        )
                        val meldingFraBehandler = generateMeldingFraBehandler(
                            conversationRef = firstConversation,
                            personIdent = personIdent,
                        )
                        database.createMeldingerFraBehandler(
                            meldingFraBehandler = meldingFraBehandler,
                            numberOfMeldinger = 2,
                        )
                        val (secondConversation, _) = database.createMeldingerTilBehandler(
                            meldingTilBehandler = defaultMeldingTilBehandler.copy(
                                conversationRef = UUID.randomUUID()
                            ),
                        )
                        with(
                            handleRequest(HttpMethod.Get, apiUrl) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val respons = objectMapper.readValue<MeldingResponseDTO>(response.content!!)
                            val conversation = respons.conversations[firstConversation]!!
                            conversation.size shouldBeEqualTo 4
                            val message = conversation.first()
                            message.tekst shouldBeEqualTo "${defaultMeldingTilBehandler.tekst}1"
                            message.document shouldBeEqualTo defaultMeldingTilBehandler.document
                            message.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER
                            conversation.all { it.status == null } shouldBeEqualTo true

                            respons.conversations[secondConversation]?.size shouldBeEqualTo 1

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            pMeldinger.size shouldBeEqualTo 5
                            pMeldinger.first().arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                            pMeldinger.first().veilederIdent shouldBeEqualTo veilederIdent
                        }
                    }
                    it("Returns meldinger for personident with status for melding til behander") {
                        database.connection.use {
                            val meldingId = it.createMeldingTilBehandler(
                                meldingTilBehandler = defaultMeldingTilBehandler,
                                commit = false,
                            )
                            it.createMeldingStatus(
                                meldingStatus = MeldingStatus(
                                    uuid = UUID.randomUUID(),
                                    status = MeldingStatusType.AVVIST,
                                    tekst = "Melding avvist",
                                ),
                                meldingId = meldingId,
                            )
                            it.commit()
                        }

                        with(
                            handleRequest(HttpMethod.Get, apiUrl) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val respons = objectMapper.readValue<MeldingResponseDTO>(response.content!!)
                            val message = respons.conversations.values.first().first()
                            message.status.shouldNotBeNull()
                            message.status?.type shouldBeEqualTo MeldingStatusType.AVVIST
                            message.status?.tekst shouldBeEqualTo "Melding avvist"
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(apiUrl, HttpMethod.Get)
                    }
                    it("returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(apiUrl, validToken, HttpMethod.Get)
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(apiUrl, validToken, HttpMethod.Get)
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(apiUrl, validToken, HttpMethod.Get)
                    }
                }
            }

            describe("Get vedlegg for melding") {
                describe("Happy path") {
                    it("Returns vedlegg for melding") {
                        val (conversation, _) = database.createMeldingerTilBehandler(
                            meldingTilBehandler = defaultMeldingTilBehandler,
                        )
                        val meldingFraBehandler = generateMeldingFraBehandler(
                            conversationRef = conversation,
                            personIdent = personIdent,
                        )
                        val vedleggNumber = 0
                        database.connection.use {
                            val meldingId = it.createMeldingFraBehandler(
                                meldingFraBehandler = meldingFraBehandler,
                                fellesformat = null,
                            )
                            it.createVedlegg(UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER, meldingId, vedleggNumber)
                            it.commit()
                        }
                        with(
                            handleRequest(HttpMethod.Get, "$apiUrl/${meldingFraBehandler.uuid}/$vedleggNumber/pdf") {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val pdfContent = response.byteContent!!
                            pdfContent shouldBeEqualTo UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER
                        }
                    }
                    it("Returns 204 when no vedlegg for melding") {
                        val (conversation, _) = database.createMeldingerTilBehandler(
                            meldingTilBehandler = defaultMeldingTilBehandler,
                        )
                        val meldingFraBehandler = generateMeldingFraBehandler(
                            conversationRef = conversation,
                            personIdent = personIdent,
                        )
                        database.connection.use {
                            it.createMeldingFraBehandler(
                                meldingFraBehandler = meldingFraBehandler,
                                fellesformat = null
                            )
                            it.commit()
                        }
                        with(
                            handleRequest(HttpMethod.Get, "$apiUrl/${meldingFraBehandler.uuid}/0/pdf") {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.NoContent
                        }
                    }
                }
                describe("Unhappy path") {
                    it("Returns BadRequest when getting vedlegg for unknown melding") {
                        val uuid = UUID.randomUUID().toString()
                        with(
                            handleRequest(HttpMethod.Get, "$apiUrl/$uuid/0/pdf") {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Returns status Unauthorized if no token is supplied") {
                        val uuid = UUID.randomUUID().toString()
                        with(
                            handleRequest(HttpMethod.Get, "$apiUrl/$uuid/0/pdf") {
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("Returns status Forbidden if denied access to person") {
                        val (conversation, _) = database.createMeldingerTilBehandler(
                            generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(
                                personIdent = UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS,
                                veilederIdent = veilederIdent,
                            ),
                        )
                        val meldingFraBehandler = generateMeldingFraBehandler(
                            conversationRef = conversation,
                            personIdent = UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS,
                        )
                        val vedleggNumber = 0
                        database.connection.use {
                            val meldingId = it.createMeldingFraBehandler(
                                meldingFraBehandler = meldingFraBehandler,
                                fellesformat = null,
                            )
                            it.createVedlegg(UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER, meldingId, vedleggNumber)
                            it.commit()
                        }
                        with(
                            handleRequest(HttpMethod.Get, "$apiUrl/${meldingFraBehandler.uuid}/$vedleggNumber/pdf") {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                }
            }
        }
    }
})
