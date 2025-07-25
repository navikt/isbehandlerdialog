package no.nav.syfo.melding.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.api.endpoints.meldingApiBasePath
import no.nav.syfo.api.models.MeldingResponseDTO
import no.nav.syfo.domain.MOTTATT_LEGEERKLARING_TEKST
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.database.createMeldingFraBehandler
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.database.createVedlegg
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.kafka.legeerklaring.toMeldingFraBehandler
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateKafkaLegeerklaringFraBehandlerDTO
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class MeldingApiGetSpek : Spek({
    describe(MeldingApiGetSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = ExternalMockEnvironment.instance.database

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
                            uuid = UUID.randomUUID(),
                            conversationRef = UUID.randomUUID(),
                        ),
                    )

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val respons = response.body<MeldingResponseDTO>()
                        val conversation = respons.conversations[firstConversation]!!
                        conversation.size shouldBeEqualTo 4
                        conversation.all { it.status == null } shouldBeEqualTo true
                        val firstMeldingDTO = conversation.first()
                        firstMeldingDTO.tekst shouldBeEqualTo defaultMeldingTilBehandler.tekst
                        firstMeldingDTO.document shouldBeEqualTo defaultMeldingTilBehandler.document
                        firstMeldingDTO.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER
                        firstMeldingDTO.conversationRef shouldBeEqualTo firstConversation
                        firstMeldingDTO.parentRef.shouldBeNull()
                        firstMeldingDTO.isFirstVedleggLegeerklaring.shouldBeFalse()
                        val lastMeldingDTO = conversation.last()
                        lastMeldingDTO.conversationRef shouldBeEqualTo firstConversation
                        lastMeldingDTO.parentRef.shouldNotBeNull()
                        lastMeldingDTO.isFirstVedleggLegeerklaring.shouldBeFalse()

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

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val respons = response.body<MeldingResponseDTO>()
                        val message = respons.conversations.values.first().first()
                        message.status.shouldNotBeNull()
                        message.status?.type shouldBeEqualTo MeldingStatusType.AVVIST
                        message.status?.tekst shouldBeEqualTo "Melding avvist"
                    }
                }
                it("Returns meldinger for personident with isFirstVedleggLegeerklaring true for legeerklaring melding fra behandler") {
                    val meldingTilBehandler = generateMeldingTilBehandler(type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)
                    val (firstConversation, _) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandler,
                    )
                    val meldingFraBehandler = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = UserConstants.BEHANDLER_PERSONIDENT,
                        behandlerNavn = UserConstants.BEHANDLER_NAVN,
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        conversationRef = firstConversation.toString(),
                        parentRef = meldingTilBehandler.uuid.toString(),
                    ).toMeldingFraBehandler(
                        parentRef = meldingTilBehandler.uuid,
                        antallVedlegg = 1,
                    )
                    database.createMeldingerFraBehandler(
                        meldingFraBehandler = meldingFraBehandler,
                    )

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val respons = response.body<MeldingResponseDTO>()
                        val conversation = respons.conversations[firstConversation]!!
                        conversation.size shouldBeEqualTo 2

                        val firstMeldingDTO = conversation.first()
                        firstMeldingDTO.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING
                        firstMeldingDTO.conversationRef shouldBeEqualTo firstConversation
                        firstMeldingDTO.isFirstVedleggLegeerklaring.shouldBeFalse()

                        val lastMeldingDTO = conversation.last()
                        lastMeldingDTO.conversationRef shouldBeEqualTo firstConversation
                        lastMeldingDTO.tekst shouldBeEqualTo MOTTATT_LEGEERKLARING_TEKST
                        lastMeldingDTO.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING
                        lastMeldingDTO.isFirstVedleggLegeerklaring.shouldBeTrue()
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

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get("$apiUrl/${meldingFraBehandler.uuid}/$vedleggNumber/pdf") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val pdfContent = response.bodyAsBytes()
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
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get("$apiUrl/${meldingFraBehandler.uuid}/0/pdf") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.NoContent
                    }
                }
            }
            describe("Unhappy path") {
                it("Returns BadRequest when getting vedlegg for unknown melding") {
                    val uuid = UUID.randomUUID().toString()

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get("$apiUrl/$uuid/0/pdf") {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("Returns status Unauthorized if no token is supplied") {
                    val uuid = UUID.randomUUID().toString()

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get("$apiUrl/$uuid/0/pdf")

                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
                it("Returns status Forbidden if denied access to person") {
                    val (conversation, _) = database.createMeldingerTilBehandler(
                        generateMeldingTilBehandler(
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

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get("$apiUrl/${meldingFraBehandler.uuid}/$vedleggNumber/pdf") {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
        }
    }
})
