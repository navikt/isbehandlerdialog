package no.nav.syfo.melding.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.domain.*
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.melding.status.database.createMeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatusType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandlerRequestDTO
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import java.util.concurrent.Future

class MeldingApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(MeldingApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = ExternalMockEnvironment.instance.database
            val kafkaProducer = mockk<KafkaProducer<String, DialogmeldingBestillingDTO>>()
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                dialogmeldingBestillingProducer = DialogmeldingBestillingProducer(
                    dialogmeldingBestillingKafkaProducer = kafkaProducer,
                ),
            )

            val apiUrl = meldingApiBasePath

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
            val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT

            beforeEachTest {
                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }
            afterEachTest {
                database.dropData()
            }

            describe("Get meldinger for person") {
                describe("Happy path") {
                    it("Returns all meldinger for personident grouped by conversationRef") {
                        val meldingTilBehandlerDTO = generateMeldingTilBehandlerRequestDTO()
                        val firstConversation = database.createMeldingerTilBehandler(
                            meldingTilBehandler = meldingTilBehandlerDTO.toMeldingTilBehandler(personIdent),
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
                        val secondConversation = database.createMeldingerTilBehandler(
                            meldingTilBehandler = meldingTilBehandlerDTO
                                .toMeldingTilBehandler(personIdent)
                                .copy(conversationRef = UUID.randomUUID()),
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
                            message.tekst shouldBeEqualTo "${meldingTilBehandlerDTO.tekst}1"
                            message.document shouldBeEqualTo meldingTilBehandlerDTO.document
                            conversation.all { it.status == null } shouldBeEqualTo true

                            respons.conversations[secondConversation]?.size shouldBeEqualTo 1

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            pMeldinger.size shouldBeEqualTo 5
                            pMeldinger.first().arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                        }
                    }
                    it("Returns meldinger for personident with status for melding til behander") {
                        val meldingTilBehandler =
                            generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(personIdent)
                        database.connection.use {
                            val meldingId =
                                it.createMeldingTilBehandler(meldingTilBehandler = meldingTilBehandler, commit = false)
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
                        val conversation = database.createMeldingerTilBehandler(
                            generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(personIdent),
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
                            it.createVedlegg(UserConstants.PDF_FORESPORSEL_OM_PASIENT, meldingId, vedleggNumber)
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
                            pdfContent shouldBeEqualTo UserConstants.PDF_FORESPORSEL_OM_PASIENT
                        }
                    }
                    it("Returns 204 when no vedlegg for melding") {
                        val conversation = database.createMeldingerTilBehandler(
                            generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(personIdent),
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
                        val conversation = database.createMeldingerTilBehandler(
                            generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS),
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
                            it.createVedlegg(UserConstants.PDF_FORESPORSEL_OM_PASIENT, meldingId, vedleggNumber)
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

            describe("Create new melding to behandler") {
                val meldingTilBehandlerDTO = generateMeldingTilBehandlerRequestDTO()

                it("Will create a new melding to behandler and produce dialogmelding-bestilling to kafka") {
                    with(
                        handleRequest(HttpMethod.Post, apiUrl) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            setBody(objectMapper.writeValueAsString(meldingTilBehandlerDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val pMeldinger = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                        pMeldinger.size shouldBeEqualTo 1
                        val pMelding = pMeldinger.first()
                        pMelding.tekst shouldBeEqualTo meldingTilBehandlerDTO.tekst
                        pMelding.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT.name
                        pMelding.innkommende shouldBeEqualTo false
                        val brodtekst =
                            pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                        brodtekst.texts.first() shouldBeEqualTo meldingTilBehandlerDTO.tekst

                        val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                        dialogmeldingBestillingDTO.behandlerRef shouldBeEqualTo meldingTilBehandlerDTO.behandlerRef.toString()
                        dialogmeldingBestillingDTO.dialogmeldingTekst shouldBeEqualTo meldingTilBehandlerDTO.document.serialize()
                        dialogmeldingBestillingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_FORESPORSEL.name
                        dialogmeldingBestillingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.FORESPORSEL.value
                        dialogmeldingBestillingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.FORESPORSEL.name
                        dialogmeldingBestillingDTO.dialogmeldingUuid shouldBeEqualTo pMelding.uuid.toString()
                        dialogmeldingBestillingDTO.personIdent shouldBeEqualTo pMelding.arbeidstakerPersonIdent
                        dialogmeldingBestillingDTO.dialogmeldingRefConversation shouldBeEqualTo pMelding.conversationRef.toString()
                        dialogmeldingBestillingDTO.dialogmeldingRefParent shouldBeEqualTo null
                        dialogmeldingBestillingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                    }
                }

                it("Store a new dialogmelding as pdf") {
                    with(
                        handleRequest(HttpMethod.Post, apiUrl) {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            setBody(objectMapper.writeValueAsString(meldingTilBehandlerDTO))
                        }
                    ) {
                        val pMeldinger = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                        val pPDFs = database.connection.getPDFs(pMeldinger.first().uuid)

                        pPDFs.size shouldBeEqualTo 1
                        pPDFs.first().pdf shouldBeEqualTo UserConstants.PDF_FORESPORSEL_OM_PASIENT
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
