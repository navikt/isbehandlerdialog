package no.nav.syfo.melding.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.domain.*
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

class MeldingApiPostSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(MeldingApiPostSpek::class.java.simpleName) {
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
            val veilederIdent = UserConstants.VEILEDER_IDENT

            beforeEachTest {
                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }
            afterEachTest {
                database.dropData()
            }

            describe("Create new paminnelse for melding to behandler") {
                val paminnelseApiUrl = "$apiUrl/${defaultMeldingTilBehandler.uuid}/paminnelse"
                val paminnelseDTO = generatePaminnelseRequestDTO()

                describe("Happy path") {
                    it("Creates paminnelse for melding til behandler and produces dialogmelding-bestilling to kafka") {
                        database.connection.use {
                            it.createMeldingTilBehandler(defaultMeldingTilBehandler)
                        }
                        with(
                            handleRequest(HttpMethod.Post, "$apiUrl/${defaultMeldingTilBehandler.uuid}/paminnelse") {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                                setBody(objectMapper.writeValueAsString(paminnelseDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val pMeldinger =
                                database.getMeldingerForArbeidstaker(personIdent)
                            pMeldinger.size shouldBeEqualTo 2
                            val pMelding = pMeldinger.last()
                            pMelding.tekst?.shouldBeEmpty()
                            pMelding.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_PAMINNELSE.name
                            pMelding.innkommende.shouldBeFalse()
                            pMelding.conversationRef shouldBeEqualTo defaultMeldingTilBehandler.conversationRef
                            pMelding.parentRef shouldBeEqualTo defaultMeldingTilBehandler.uuid
                            val brodtekst =
                                pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                            brodtekst.texts.first() shouldBeEqualTo "Vi viser til tidligere forespørsel angående din pasient"

                            val pPdf = database.firstPdf(meldingUuid = pMelding.uuid)
                            pPdf.pdf shouldBeEqualTo UserConstants.PDF_FORESPORSEL_OM_PASIENT_PAMINNELSE

                            val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                            dialogmeldingBestillingDTO.behandlerRef shouldBeEqualTo defaultMeldingTilBehandler.behandlerRef.toString()
                            dialogmeldingBestillingDTO.dialogmeldingTekst shouldBeEqualTo paminnelseDTO.document.serialize()
                            dialogmeldingBestillingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_FORESPORSEL.name
                            dialogmeldingBestillingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.PAMINNELSE_FORESPORSEL.value
                            dialogmeldingBestillingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.FORESPORSEL.name
                            dialogmeldingBestillingDTO.dialogmeldingUuid shouldBeEqualTo pMelding.uuid.toString()
                            dialogmeldingBestillingDTO.personIdent shouldBeEqualTo pMelding.arbeidstakerPersonIdent
                            dialogmeldingBestillingDTO.dialogmeldingRefConversation shouldBeEqualTo pMelding.conversationRef.toString()
                            dialogmeldingBestillingDTO.dialogmeldingRefParent shouldBeEqualTo pMelding.parentRef.toString()
                            dialogmeldingBestillingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        }
                    }
                }
                describe("Unnhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(paminnelseApiUrl, HttpMethod.Post)
                    }
                    it("returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(paminnelseApiUrl, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(paminnelseApiUrl, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(paminnelseApiUrl, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if no melding exists for given uuid") {
                        with(
                            handleRequest(HttpMethod.Post, paminnelseApiUrl) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    personIdent.value
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if given uuid is meldingFraBehandler") {
                        val (conversation, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                        val meldingFraBehandler = generateMeldingFraBehandler(
                            conversationRef = conversation,
                            personIdent = personIdent,
                        )
                        database.connection.use {
                            it.createMeldingFraBehandler(
                                meldingFraBehandler = meldingFraBehandler,
                                fellesformat = null,
                            )
                            it.commit()
                        }

                        with(
                            handleRequest(HttpMethod.Post, "$apiUrl/${meldingFraBehandler.uuid}/paminnelse") {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    personIdent.value
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
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
                            addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                            setBody(objectMapper.writeValueAsString(meldingTilBehandlerDTO))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        pMeldinger.size shouldBeEqualTo 1
                        val pMelding = pMeldinger.first()
                        pMelding.tekst shouldBeEqualTo meldingTilBehandlerDTO.tekst
                        pMelding.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name
                        pMelding.innkommende shouldBeEqualTo false
                        pMelding.veilederIdent shouldBeEqualTo veilederIdent

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
                            addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                            setBody(objectMapper.writeValueAsString(meldingTilBehandlerDTO))
                        }
                    ) {
                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        val pPdf = database.firstPdf(meldingUuid = pMeldinger.first().uuid)
                        pPdf.pdf shouldBeEqualTo UserConstants.PDF_FORESPORSEL_OM_PASIENT
                    }
                }
            }
        }
    }
})
