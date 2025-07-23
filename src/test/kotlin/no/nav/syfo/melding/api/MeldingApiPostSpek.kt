package no.nav.syfo.melding.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.api.endpoints.meldingApiBasePath
import no.nav.syfo.domain.DocumentComponentType
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.domain.serialize
import no.nav.syfo.infrastructure.database.createMeldingFraBehandler
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingBestillingDTO
import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingKode
import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingKodeverk
import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingType
import no.nav.syfo.infrastructure.kafka.producer.DialogmeldingBestillingProducer
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import java.util.concurrent.Future

class MeldingApiPostSpek : Spek({
    describe(MeldingApiPostSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = ExternalMockEnvironment.instance.database
        val kafkaProducer = mockk<KafkaProducer<String, DialogmeldingBestillingDTO>>()
        val dialogmeldingBestillingProducer = DialogmeldingBestillingProducer(
            dialogmeldingBestillingKafkaProducer = kafkaProducer,
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

                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post("$apiUrl/${defaultMeldingTilBehandler.uuid}/paminnelse") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(paminnelseDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

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
                        dialogmeldingBestillingDTO.kilde shouldBeEqualTo "SYFO"
                    }
                }
            }
            describe("Unhappy path") {
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
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(paminnelseApiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(paminnelseDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
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

                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post("$apiUrl/${meldingFraBehandler.uuid}/paminnelse") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(paminnelseDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("returns status BadRequest if given uuid is melding til behandler påminnelse") {
                    val meldingTilBehandlerPaminnelse = generateMeldingTilBehandler(type = MeldingType.FORESPORSEL_PASIENT_PAMINNELSE)
                    database.connection.use {
                        it.createMeldingTilBehandler(meldingTilBehandlerPaminnelse)
                    }

                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post("$apiUrl/${meldingTilBehandlerPaminnelse.uuid}/paminnelse") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(paminnelseDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("returns status BadRequest if given uuid is melding til behandler henvendelse melding fra NAV") {
                    val meldingFraNav = generateMeldingTilBehandler(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV)
                    database.connection.use {
                        it.createMeldingTilBehandler(meldingFraNav)
                    }

                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post("$apiUrl/${meldingFraNav.uuid}/paminnelse") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(paminnelseDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }

        describe("Create new melding to behandler") {
            describe("Happy path") {
                describe("Foresporsel pasient tilleggsopplysninger") {
                    val meldingTilBehandlerDTO = generateMeldingTilBehandlerRequestDTO()

                    it("Will create a new melding to behandler and produce dialogmelding-bestilling to kafka") {
                        testApplication {
                            val client = setupApiAndClient(dialogmeldingBestillingProducer)
                            val response = client.post(apiUrl) {
                                bearerAuth(validToken)
                                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                                contentType(ContentType.Application.Json)
                                setBody(meldingTilBehandlerDTO)
                            }

                            response.status shouldBeEqualTo HttpStatusCode.OK

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
                        testApplication {
                            val client = setupApiAndClient(dialogmeldingBestillingProducer)
                            val response = client.post(apiUrl) {
                                bearerAuth(validToken)
                                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                                contentType(ContentType.Application.Json)
                                setBody(meldingTilBehandlerDTO)
                            }

                            response.status shouldBeEqualTo HttpStatusCode.OK

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            val pPdf = database.firstPdf(meldingUuid = pMeldinger.first().uuid)
                            pPdf.pdf shouldBeEqualTo UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER
                        }
                    }
                }
                describe("Foresporsel pasient legeerklæring") {
                    val meldingTilBehandlerDTO = generateMeldingTilBehandlerRequestDTO(type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)

                    it("Will create a new melding to behandler and produce dialogmelding-bestilling to kafka") {
                        testApplication {
                            val client = setupApiAndClient(dialogmeldingBestillingProducer)
                            val response = client.post(apiUrl) {
                                bearerAuth(validToken)
                                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                                contentType(ContentType.Application.Json)
                                setBody(meldingTilBehandlerDTO)
                            }

                            response.status shouldBeEqualTo HttpStatusCode.OK

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            pMeldinger.size shouldBeEqualTo 1
                            val pMelding = pMeldinger.first()
                            pMelding.tekst shouldBeEqualTo meldingTilBehandlerDTO.tekst
                            pMelding.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
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
                        testApplication {
                            val client = setupApiAndClient(dialogmeldingBestillingProducer)
                            val response = client.post(apiUrl) {
                                bearerAuth(validToken)
                                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                                contentType(ContentType.Application.Json)
                                setBody(meldingTilBehandlerDTO)
                            }

                            response.status shouldBeEqualTo HttpStatusCode.OK

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            val pPdf = database.firstPdf(meldingUuid = pMeldinger.first().uuid)
                            pPdf.pdf shouldBeEqualTo UserConstants.PDF_FORESPORSEL_OM_PASIENT_LEGEERKLARING
                        }
                    }
                }

                describe("Henvendelse melding fra nav") {
                    val meldingTilBehandlerDTO = generateMeldingTilBehandlerRequestDTO()
                        .copy(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV)

                    it("Will create a new melding fra nav and produce dialogmelding-bestilling to kafka") {
                        testApplication {
                            val client = setupApiAndClient(dialogmeldingBestillingProducer)
                            val response = client.post(apiUrl) {
                                bearerAuth(validToken)
                                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                                contentType(ContentType.Application.Json)
                                setBody(meldingTilBehandlerDTO)
                            }

                            response.status shouldBeEqualTo HttpStatusCode.OK

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            pMeldinger.size shouldBeEqualTo 1
                            val pMelding = pMeldinger.first()
                            pMelding.tekst shouldBeEqualTo meldingTilBehandlerDTO.tekst
                            pMelding.type shouldBeEqualTo MeldingType.HENVENDELSE_MELDING_FRA_NAV.name
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
                            dialogmeldingBestillingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_NOTAT.name
                            dialogmeldingBestillingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.MELDING_FRA_NAV.value
                            dialogmeldingBestillingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE.name
                            dialogmeldingBestillingDTO.dialogmeldingUuid shouldBeEqualTo pMelding.uuid.toString()
                            dialogmeldingBestillingDTO.personIdent shouldBeEqualTo pMelding.arbeidstakerPersonIdent
                            dialogmeldingBestillingDTO.dialogmeldingRefConversation shouldBeEqualTo pMelding.conversationRef.toString()
                            dialogmeldingBestillingDTO.dialogmeldingRefParent shouldBeEqualTo null
                            dialogmeldingBestillingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                        }
                    }

                    it("Creates pdf for melding fra NAV") {
                        testApplication {
                            val client = setupApiAndClient(dialogmeldingBestillingProducer)
                            val response = client.post(apiUrl) {
                                bearerAuth(validToken)
                                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                                contentType(ContentType.Application.Json)
                                setBody(meldingTilBehandlerDTO)
                            }

                            response.status shouldBeEqualTo HttpStatusCode.OK

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            val pPdf = database.firstPdf(meldingUuid = pMeldinger.first().uuid)
                            pPdf.pdf shouldBeEqualTo UserConstants.PDF_HENVENDELSE_MELDING_FRA_NAV
                        }
                    }
                }
            }
            describe("Unhappy path") {
                it("Returns status Unauthorized if no token is supplied") {
                    testMissingToken(apiUrl, HttpMethod.Post)
                }
                it("returns status Forbidden if denied access to person") {
                    testDeniedPersonAccess(apiUrl, validToken, HttpMethod.Post)
                }
                it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testMissingPersonIdent(apiUrl, validToken, HttpMethod.Post)
                }
                it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testInvalidPersonIdent(apiUrl, validToken, HttpMethod.Post)
                }
            }
        }

        describe("Create retur av legeerklæring") {
            val returApiUrl = "$apiUrl/${defaultMeldingFraBehandler.uuid}/retur"
            val returDTO = generateReturAvLegeerklaringRequestDTO()

            describe("Happy path") {
                it("Creates retur av legeerklæring for melding fra behandler and produces dialogmelding-bestilling to kafka") {
                    val foresporselLegeerklaring = generateMeldingTilBehandler(
                        type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
                    )
                    val paminnelse = foresporselLegeerklaring.copy(
                        tekst = "",
                        type = MeldingType.FORESPORSEL_PASIENT_PAMINNELSE,
                        document = generatePaminnelseRequestDTO().document,
                        uuid = UUID.randomUUID(),
                    )
                    val innkommendeLegeerklaring = generateMeldingFraBehandler().copy(
                        conversationRef = foresporselLegeerklaring.conversationRef,
                        type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
                    )

                    database.connection.use {
                        it.createMeldingTilBehandler(foresporselLegeerklaring)
                        it.createMeldingTilBehandler(paminnelse)
                        it.createMeldingFraBehandler(
                            meldingFraBehandler = innkommendeLegeerklaring,
                            commit = true,
                        )
                    }

                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post("$apiUrl/${innkommendeLegeerklaring.uuid}/retur") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(returDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val pMeldinger =
                            database.getMeldingerForArbeidstaker(personIdent)
                        pMeldinger.size shouldBeEqualTo 4
                        val pMelding = pMeldinger.last()
                        pMelding.tekst shouldBeEqualTo returDTO.tekst
                        pMelding.type shouldBeEqualTo MeldingType.HENVENDELSE_RETUR_LEGEERKLARING.name
                        pMelding.innkommende.shouldBeFalse()
                        pMelding.conversationRef shouldBeEqualTo foresporselLegeerklaring.conversationRef
                        pMelding.parentRef shouldBeEqualTo innkommendeLegeerklaring.uuid
                        val brodtekst =
                            pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                        brodtekst.texts.first() shouldBeEqualTo "Vi viser til tidligere legeerklæring utsendt for din pasient"

                        val pPdf = database.firstPdf(meldingUuid = pMelding.uuid)
                        pPdf.pdf shouldBeEqualTo UserConstants.PDF_RETUR_LEGEERKLARING

                        val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                        dialogmeldingBestillingDTO.behandlerRef shouldBeEqualTo foresporselLegeerklaring.behandlerRef.toString()
                        dialogmeldingBestillingDTO.dialogmeldingTekst shouldBeEqualTo returDTO.document.serialize()
                        dialogmeldingBestillingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_NOTAT.name
                        dialogmeldingBestillingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.RETUR_LEGEERKLARING.value
                        dialogmeldingBestillingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE.name
                        dialogmeldingBestillingDTO.dialogmeldingUuid shouldBeEqualTo pMelding.uuid.toString()
                        dialogmeldingBestillingDTO.personIdent shouldBeEqualTo pMelding.arbeidstakerPersonIdent
                        dialogmeldingBestillingDTO.dialogmeldingRefConversation shouldBeEqualTo pMelding.conversationRef.toString()
                        dialogmeldingBestillingDTO.dialogmeldingRefParent shouldBeEqualTo pMelding.parentRef.toString()
                        dialogmeldingBestillingDTO.dialogmeldingVedlegg shouldNotBeEqualTo null
                    }
                }
            }

            describe("Unhappy path") {
                it("Returns status Unauthorized if no token is supplied") {
                    testMissingToken(returApiUrl, HttpMethod.Post)
                }
                it("returns status Forbidden if denied access to person") {
                    testDeniedPersonAccess(returApiUrl, validToken, HttpMethod.Post)
                }
                it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testMissingPersonIdent(returApiUrl, validToken, HttpMethod.Post)
                }
                it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testInvalidPersonIdent(returApiUrl, validToken, HttpMethod.Post)
                }
                it("returns status BadRequest if no meldingFraBehandler exists for given uuid") {
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(returApiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(returDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("returns status BadRequest if meldingFraBehandler (not legeerklaring) exists for given uuid") {
                    val foresporselTilleggsopplysninger = generateMeldingTilBehandler(
                        type = MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER,
                    )
                    val foresporselSvar = generateMeldingFraBehandler().copy(
                        conversationRef = foresporselTilleggsopplysninger.conversationRef,
                        type = MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER,
                    )

                    database.connection.use {
                        it.createMeldingTilBehandler(foresporselTilleggsopplysninger)
                        it.createMeldingFraBehandler(
                            meldingFraBehandler = foresporselSvar,
                            commit = true,
                        )
                    }

                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post("$apiUrl/${foresporselSvar.uuid}/retur") {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(returDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }
})
