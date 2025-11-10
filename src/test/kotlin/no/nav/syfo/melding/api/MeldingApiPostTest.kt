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
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.util.*
import java.util.concurrent.Future

class MeldingApiPostTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = ExternalMockEnvironment.instance.database
    private val kafkaProducer = mockk<KafkaProducer<String, DialogmeldingBestillingDTO>>()
    private val dialogmeldingBestillingProducer = DialogmeldingBestillingProducer(
        dialogmeldingBestillingKafkaProducer = kafkaProducer,
    )

    private val apiUrl = meldingApiBasePath
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    private val veilederIdent = UserConstants.VEILEDER_IDENT

    @BeforeEach
    fun beforeEach() {
        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Create new paminnelse for melding to behandler")
    inner class CreateNewPaminnelse {

        private val paminnelseApiUrl = "$apiUrl/${defaultMeldingTilBehandler.uuid}/paminnelse"
        private val paminnelseDTO = generatePaminnelseRequestDTO()

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Creates paminnelse for melding til behandler and produces dialogmelding-bestilling to kafka`() {
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

                    assertEquals(HttpStatusCode.OK, response.status)

                    val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                    assertEquals(2, pMeldinger.size)
                    val pMelding = pMeldinger.last()
                    assertTrue(pMelding.tekst!!.isEmpty())
                    assertEquals(MeldingType.FORESPORSEL_PASIENT_PAMINNELSE.name, pMelding.type)
                    assertFalse(pMelding.innkommende)
                    assertEquals(defaultMeldingTilBehandler.conversationRef, pMelding.conversationRef)
                    assertEquals(defaultMeldingTilBehandler.uuid, pMelding.parentRef)
                    val brodtekst =
                        pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                    assertEquals("Vi viser til tidligere forespørsel angående din pasient", brodtekst.texts.first())

                    val pPdf = database.firstPdf(meldingUuid = pMelding.uuid)
                    assertArrayEquals(UserConstants.PDF_FORESPORSEL_OM_PASIENT_PAMINNELSE, pPdf.pdf)

                    val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                    assertEquals(
                        defaultMeldingTilBehandler.behandlerRef.toString(),
                        dialogmeldingBestillingDTO.behandlerRef
                    )
                    assertEquals(paminnelseDTO.document.serialize(), dialogmeldingBestillingDTO.dialogmeldingTekst)
                    assertEquals(
                        DialogmeldingType.DIALOG_FORESPORSEL.name,
                        dialogmeldingBestillingDTO.dialogmeldingType
                    )
                    assertEquals(
                        DialogmeldingKode.PAMINNELSE_FORESPORSEL.value,
                        dialogmeldingBestillingDTO.dialogmeldingKode
                    )
                    assertEquals(
                        DialogmeldingKodeverk.FORESPORSEL.name,
                        dialogmeldingBestillingDTO.dialogmeldingKodeverk
                    )
                    assertEquals(pMelding.uuid.toString(), dialogmeldingBestillingDTO.dialogmeldingUuid)
                    assertEquals(pMelding.arbeidstakerPersonIdent, dialogmeldingBestillingDTO.personIdent)
                    assertEquals(
                        pMelding.conversationRef.toString(),
                        dialogmeldingBestillingDTO.dialogmeldingRefConversation
                    )
                    assertEquals(pMelding.parentRef.toString(), dialogmeldingBestillingDTO.dialogmeldingRefParent)
                    assertNotNull(dialogmeldingBestillingDTO.dialogmeldingVedlegg)
                    assertEquals("SYFO", dialogmeldingBestillingDTO.kilde)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Returns status Unauthorized if no token is supplied`() {
                testMissingToken(paminnelseApiUrl, HttpMethod.Post)
            }

            @Test
            fun `returns status Forbidden if denied access to person`() {
                testDeniedPersonAccess(paminnelseApiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
                testMissingPersonIdent(paminnelseApiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
                testInvalidPersonIdent(paminnelseApiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no melding exists for given uuid`() {
                testApplication {
                    val client = setupApiAndClient(dialogmeldingBestillingProducer)
                    val response = client.post(paminnelseApiUrl) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personIdent.value)
                        contentType(ContentType.Application.Json)
                        setBody(paminnelseDTO)
                    }

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status BadRequest if given uuid is meldingFraBehandler`() {
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

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status BadRequest if given uuid is melding til behandler påminnelse`() {
                val meldingTilBehandlerPaminnelse =
                    generateMeldingTilBehandler(type = MeldingType.FORESPORSEL_PASIENT_PAMINNELSE)
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

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status BadRequest if given uuid is melding til behandler henvendelse melding fra NAV`() {
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

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }
        }
    }

    @Nested
    @DisplayName("Create new melding to behandler")
    inner class CreateNewMeldingToBehandler {

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Nested
            @DisplayName("Foresporsel pasient tilleggsopplysninger")
            inner class ForesporselPasientTilleggsopplysninger {

                private val meldingTilBehandlerDTO = generateMeldingTilBehandlerRequestDTO()

                @Test
                fun `Will create a new melding to behandler and produce dialogmelding-bestilling to kafka`() {
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(meldingTilBehandlerDTO)
                        }

                        assertEquals(HttpStatusCode.OK, response.status)

                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        assertEquals(1, pMeldinger.size)
                        val pMelding = pMeldinger.first()
                        assertEquals(meldingTilBehandlerDTO.tekst, pMelding.tekst)
                        assertEquals(MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name, pMelding.type)
                        assertEquals(false, pMelding.innkommende)
                        assertEquals(veilederIdent, pMelding.veilederIdent)

                        val brodtekst =
                            pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                        assertEquals(meldingTilBehandlerDTO.tekst, brodtekst.texts.first())

                        val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                        assertEquals(
                            meldingTilBehandlerDTO.behandlerRef.toString(),
                            dialogmeldingBestillingDTO.behandlerRef
                        )
                        assertEquals(
                            meldingTilBehandlerDTO.document.serialize(),
                            dialogmeldingBestillingDTO.dialogmeldingTekst
                        )
                        assertEquals(
                            DialogmeldingType.DIALOG_FORESPORSEL.name,
                            dialogmeldingBestillingDTO.dialogmeldingType
                        )
                        assertEquals(DialogmeldingKode.FORESPORSEL.value, dialogmeldingBestillingDTO.dialogmeldingKode)
                        assertEquals(
                            DialogmeldingKodeverk.FORESPORSEL.name,
                            dialogmeldingBestillingDTO.dialogmeldingKodeverk
                        )
                        assertEquals(pMelding.uuid.toString(), dialogmeldingBestillingDTO.dialogmeldingUuid)
                        assertEquals(pMelding.arbeidstakerPersonIdent, dialogmeldingBestillingDTO.personIdent)
                        assertEquals(
                            pMelding.conversationRef.toString(),
                            dialogmeldingBestillingDTO.dialogmeldingRefConversation
                        )
                        assertNull(dialogmeldingBestillingDTO.dialogmeldingRefParent)
                        assertNotNull(dialogmeldingBestillingDTO.dialogmeldingVedlegg)
                    }
                }

                @Test
                fun `Store a new dialogmelding as pdf`() {
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(meldingTilBehandlerDTO)
                        }

                        assertEquals(HttpStatusCode.OK, response.status)

                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        val pPdf = database.firstPdf(meldingUuid = pMeldinger.first().uuid)
                        assertArrayEquals(UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER, pPdf.pdf)
                    }
                }
            }

            @Nested
            @DisplayName("Foresporsel pasient legeerklæring")
            inner class ForesporselPasientLegeerklaring {

                private val meldingTilBehandlerDTO =
                    generateMeldingTilBehandlerRequestDTO(type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)

                @Test
                fun `Will create a new melding to behandler and produce dialogmelding-bestilling to kafka`() {
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(meldingTilBehandlerDTO)
                        }

                        assertEquals(HttpStatusCode.OK, response.status)

                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        assertEquals(1, pMeldinger.size)
                        val pMelding = pMeldinger.first()
                        assertEquals(meldingTilBehandlerDTO.tekst, pMelding.tekst)
                        assertEquals(MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name, pMelding.type)
                        assertEquals(false, pMelding.innkommende)
                        assertEquals(veilederIdent, pMelding.veilederIdent)

                        val brodtekst =
                            pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                        assertEquals(meldingTilBehandlerDTO.tekst, brodtekst.texts.first())

                        val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                        assertEquals(
                            meldingTilBehandlerDTO.behandlerRef.toString(),
                            dialogmeldingBestillingDTO.behandlerRef
                        )
                        assertEquals(
                            meldingTilBehandlerDTO.document.serialize(),
                            dialogmeldingBestillingDTO.dialogmeldingTekst
                        )
                        assertEquals(
                            DialogmeldingType.DIALOG_FORESPORSEL.name,
                            dialogmeldingBestillingDTO.dialogmeldingType
                        )
                        assertEquals(DialogmeldingKode.FORESPORSEL.value, dialogmeldingBestillingDTO.dialogmeldingKode)
                        assertEquals(
                            DialogmeldingKodeverk.FORESPORSEL.name,
                            dialogmeldingBestillingDTO.dialogmeldingKodeverk
                        )
                        assertEquals(pMelding.uuid.toString(), dialogmeldingBestillingDTO.dialogmeldingUuid)
                        assertEquals(pMelding.arbeidstakerPersonIdent, dialogmeldingBestillingDTO.personIdent)
                        assertEquals(
                            pMelding.conversationRef.toString(),
                            dialogmeldingBestillingDTO.dialogmeldingRefConversation
                        )
                        assertNull(dialogmeldingBestillingDTO.dialogmeldingRefParent)
                        assertNotNull(dialogmeldingBestillingDTO.dialogmeldingVedlegg)
                    }
                }

                @Test
                fun `Store a new dialogmelding as pdf`() {
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(meldingTilBehandlerDTO)
                        }

                        assertEquals(HttpStatusCode.OK, response.status)

                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        val pPdf = database.firstPdf(meldingUuid = pMeldinger.first().uuid)
                        assertArrayEquals(UserConstants.PDF_FORESPORSEL_OM_PASIENT_LEGEERKLARING, pPdf.pdf)
                    }
                }
            }

            @Nested
            @DisplayName("Henvendelse melding fra nav")
            inner class HenvendelseMeldingFraNAV {

                private val meldingTilBehandlerDTO = generateMeldingTilBehandlerRequestDTO()
                    .copy(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV)

                @Test
                fun `Will create a new melding fra nav and produce dialogmelding-bestilling to kafka`() {
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(meldingTilBehandlerDTO)
                        }

                        assertEquals(HttpStatusCode.OK, response.status)

                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        assertEquals(1, pMeldinger.size)
                        val pMelding = pMeldinger.first()
                        assertEquals(meldingTilBehandlerDTO.tekst, pMelding.tekst)
                        assertEquals(MeldingType.HENVENDELSE_MELDING_FRA_NAV.name, pMelding.type)
                        assertFalse(pMelding.innkommende)
                        assertEquals(veilederIdent, pMelding.veilederIdent)

                        val brodtekst =
                            pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                        assertEquals(meldingTilBehandlerDTO.tekst, brodtekst.texts.first())

                        val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                        assertEquals(
                            meldingTilBehandlerDTO.behandlerRef.toString(),
                            dialogmeldingBestillingDTO.behandlerRef
                        )
                        assertEquals(
                            meldingTilBehandlerDTO.document.serialize(),
                            dialogmeldingBestillingDTO.dialogmeldingTekst
                        )
                        assertEquals(DialogmeldingType.DIALOG_NOTAT.name, dialogmeldingBestillingDTO.dialogmeldingType)
                        assertEquals(DialogmeldingKode.MELDING_FRA_NAV.value, dialogmeldingBestillingDTO.dialogmeldingKode)
                        assertEquals(DialogmeldingKodeverk.HENVENDELSE.name, dialogmeldingBestillingDTO.dialogmeldingKodeverk)
                        assertEquals(pMelding.uuid.toString(), dialogmeldingBestillingDTO.dialogmeldingUuid)
                        assertEquals(pMelding.arbeidstakerPersonIdent, dialogmeldingBestillingDTO.personIdent)
                        assertEquals(
                            pMelding.conversationRef.toString(),
                            dialogmeldingBestillingDTO.dialogmeldingRefConversation
                        )
                        assertNull(dialogmeldingBestillingDTO.dialogmeldingRefParent)
                        assertNotNull(dialogmeldingBestillingDTO.dialogmeldingVedlegg)
                    }
                }

                @Test
                fun `Creates pdf for melding fra NAV`() {
                    testApplication {
                        val client = setupApiAndClient(dialogmeldingBestillingProducer)
                        val response = client.post(apiUrl) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdent.value)
                            contentType(ContentType.Application.Json)
                            setBody(meldingTilBehandlerDTO)
                        }

                        assertEquals(HttpStatusCode.OK, response.status)

                        val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                        val pPdf = database.firstPdf(meldingUuid = pMeldinger.first().uuid)
                        assertArrayEquals(UserConstants.PDF_HENVENDELSE_MELDING_FRA_NAV, pPdf.pdf)
                    }
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Returns status Unauthorized if no token is supplied`() {
                testMissingToken(apiUrl, HttpMethod.Post)
            }

            @Test
            fun `returns status Forbidden if denied access to person`() {
                testDeniedPersonAccess(apiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
                testMissingPersonIdent(apiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
                testInvalidPersonIdent(apiUrl, validToken, HttpMethod.Post)
            }
        }
    }

    @Nested
    @DisplayName("Create retur av legeerklæring")
    inner class CreateReturAvLegeerklaring {

        private val returApiUrl = "$apiUrl/${UUID.randomUUID()}/retur"
        private val returDTO = generateReturAvLegeerklaringRequestDTO()

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Creates retur av legeerklæring for melding fra behandler and produces dialogmelding-bestilling to kafka`() {
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

                    assertEquals(HttpStatusCode.OK, response.status)

                    val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                    assertEquals(4, pMeldinger.size)
                    val pMelding = pMeldinger.last()
                    assertEquals(returDTO.tekst, pMelding.tekst)
                    assertEquals(MeldingType.HENVENDELSE_RETUR_LEGEERKLARING.name, pMelding.type)
                    assertFalse(pMelding.innkommende)
                    assertEquals(foresporselLegeerklaring.conversationRef, pMelding.conversationRef)
                    assertEquals(innkommendeLegeerklaring.uuid, pMelding.parentRef)
                    val brodtekst =
                        pMelding.document.first { it.key == null && it.type == DocumentComponentType.PARAGRAPH }
                    assertEquals(
                        "Vi viser til tidligere legeerklæring utsendt for din pasient",
                        brodtekst.texts.first()
                    )

                    val pPdf = database.firstPdf(meldingUuid = pMelding.uuid)
                    assertArrayEquals(UserConstants.PDF_RETUR_LEGEERKLARING, pPdf.pdf)

                    val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                    assertEquals(
                        foresporselLegeerklaring.behandlerRef.toString(),
                        dialogmeldingBestillingDTO.behandlerRef
                    )
                    assertEquals(returDTO.document.serialize(), dialogmeldingBestillingDTO.dialogmeldingTekst)
                    assertEquals(DialogmeldingType.DIALOG_NOTAT.name, dialogmeldingBestillingDTO.dialogmeldingType)
                    assertEquals(DialogmeldingKode.RETUR_LEGEERKLARING.value, dialogmeldingBestillingDTO.dialogmeldingKode)
                    assertEquals(DialogmeldingKodeverk.HENVENDELSE.name, dialogmeldingBestillingDTO.dialogmeldingKodeverk)
                    assertEquals(pMelding.uuid.toString(), dialogmeldingBestillingDTO.dialogmeldingUuid)
                    assertEquals(pMelding.arbeidstakerPersonIdent, dialogmeldingBestillingDTO.personIdent)
                    assertEquals(
                        pMelding.conversationRef.toString(),
                        dialogmeldingBestillingDTO.dialogmeldingRefConversation
                    )
                    assertEquals(pMelding.parentRef.toString(), dialogmeldingBestillingDTO.dialogmeldingRefParent)
                    assertNotNull(dialogmeldingBestillingDTO.dialogmeldingVedlegg)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Returns status Unauthorized if no token is supplied`() {
                testMissingToken(returApiUrl, HttpMethod.Post)
            }

            @Test
            fun `returns status Forbidden if denied access to person`() {
                testDeniedPersonAccess(returApiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
                testMissingPersonIdent(returApiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
                testInvalidPersonIdent(returApiUrl, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no meldingFraBehandler exists for given uuid`() {
                testApplication {
                    val client = setupApiAndClient(dialogmeldingBestillingProducer)
                    val response = client.post(returApiUrl) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personIdent.value)
                        contentType(ContentType.Application.Json)
                        setBody(returDTO)
                    }

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status BadRequest if meldingFraBehandler (not legeerklaring) exists for given uuid`() {
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

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }
        }
    }
}
