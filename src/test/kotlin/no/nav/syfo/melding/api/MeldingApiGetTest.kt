package no.nav.syfo.melding.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.api.endpoints.meldingApiBasePath
import no.nav.syfo.api.models.MeldingResponseDTO
import no.nav.syfo.domain.MOTTATT_LEGEERKLARING_TEKST
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.infrastructure.database.*
import no.nav.syfo.infrastructure.kafka.legeerklaring.toMeldingFraBehandler
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateKafkaLegeerklaringFraBehandlerDTO
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.util.*

class MeldingApiGetTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = ExternalMockEnvironment.instance.database
    private val meldingRepository = ExternalMockEnvironment.instance.meldingRepository
    private val apiUrl = meldingApiBasePath
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    private val veilederIdent = UserConstants.VEILEDER_IDENT

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Get meldinger for person")
    inner class GetMeldingerForPerson {

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Returns all meldinger for personident grouped by conversationRef`() {
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

                    assertEquals(HttpStatusCode.OK, response.status)
                    val respons = response.body<MeldingResponseDTO>()
                    val conversation = respons.conversations[firstConversation]!!
                    assertEquals(4, conversation.size)
                    assertTrue(conversation.all { it.status == null })
                    val firstMeldingDTO = conversation.first()
                    assertEquals(defaultMeldingTilBehandler.tekst, firstMeldingDTO.tekst)
                    assertEquals(defaultMeldingTilBehandler.document, firstMeldingDTO.document)
                    assertEquals(Melding.MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, firstMeldingDTO.type)
                    assertEquals(firstConversation, firstMeldingDTO.conversationRef)
                    assertNull(firstMeldingDTO.parentRef)
                    assertFalse(firstMeldingDTO.isFirstVedleggLegeerklaring)
                    val lastMeldingDTO = conversation.last()
                    assertEquals(firstConversation, lastMeldingDTO.conversationRef)
                    assertNotNull(lastMeldingDTO.parentRef)
                    assertFalse(lastMeldingDTO.isFirstVedleggLegeerklaring)

                    assertEquals(1, respons.conversations[secondConversation]?.size)

                    val pMeldinger = meldingRepository.getMeldingerForArbeidstaker(personIdent)
                    assertEquals(5, pMeldinger.size)
                    assertEquals(personIdent.value, pMeldinger.first().arbeidstakerPersonIdent)
                    assertEquals(veilederIdent, pMeldinger.first().veilederIdent)
                }
            }

            @Test
            fun `Returns meldinger for personident with status for melding til behander`() {
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

                    assertEquals(HttpStatusCode.OK, response.status)
                    val respons = response.body<MeldingResponseDTO>()
                    val message = respons.conversations.values.first().first()
                    assertNotNull(message.status)
                    assertEquals(MeldingStatusType.AVVIST, message.status?.type)
                    assertEquals("Melding avvist", message.status?.tekst)
                }
            }

            @Test
            fun `Returns meldinger for personident with isFirstVedleggLegeerklaring true for legeerklaring melding fra behandler`() {
                val meldingTilBehandler = generateMeldingTilBehandler(type = Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)
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

                    assertEquals(HttpStatusCode.OK, response.status)
                    val respons = response.body<MeldingResponseDTO>()
                    val conversation = respons.conversations[firstConversation]!!
                    assertEquals(2, conversation.size)

                    val firstMeldingDTO = conversation.first()
                    assertEquals(Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, firstMeldingDTO.type)
                    assertEquals(firstConversation, firstMeldingDTO.conversationRef)
                    assertFalse(firstMeldingDTO.isFirstVedleggLegeerklaring)

                    val lastMeldingDTO = conversation.last()
                    assertEquals(firstConversation, lastMeldingDTO.conversationRef)
                    assertEquals(MOTTATT_LEGEERKLARING_TEKST, lastMeldingDTO.tekst)
                    assertEquals(Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, lastMeldingDTO.type)
                    assertTrue(lastMeldingDTO.isFirstVedleggLegeerklaring)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Returns status Unauthorized if no token is supplied`() {
                testMissingToken(apiUrl, HttpMethod.Get)
            }

            @Test
            fun `returns status Forbidden if denied access to person`() {
                testDeniedPersonAccess(apiUrl, validToken, HttpMethod.Get)
            }

            @Test
            fun `returns status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
                testMissingPersonIdent(apiUrl, validToken, HttpMethod.Get)
            }

            @Test
            fun `returns status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
                testInvalidPersonIdent(apiUrl, validToken, HttpMethod.Get)
            }
        }
    }

    @Nested
    @DisplayName("Get vedlegg for melding")
    inner class GetVedleggForMelding {

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Returns vedlegg for melding`() {
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

                    assertEquals(HttpStatusCode.OK, response.status)
                    val pdfContent = response.bodyAsBytes()
                    assertArrayEquals(UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER, pdfContent)
                }
            }

            @Test
            fun `Returns 204 when no vedlegg for melding`() {
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

                    assertEquals(HttpStatusCode.NoContent, response.status)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Returns BadRequest when getting vedlegg for unknown melding`() {
                val uuid = UUID.randomUUID().toString()

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$apiUrl/$uuid/0/pdf") {
                        bearerAuth(validToken)
                    }

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `Returns status Unauthorized if no token is supplied`() {
                val uuid = UUID.randomUUID().toString()

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get("$apiUrl/$uuid/0/pdf")

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                }
            }

            @Test
            fun `Returns status Forbidden if denied access to person`() {
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

                    assertEquals(HttpStatusCode.Forbidden, response.status)
                }
            }
        }
    }
}
