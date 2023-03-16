package no.nav.syfo.melding.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.melding.domain.*
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandlerRequestDTO
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID
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
                    produceDialogmeldingBestillingEnabled = externalMockEnvironment.environment.produceBehandlerDialogmeldingBestilling
                ),
            )

            val apiUrl = meldingApiBasePath

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )

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
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
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
                        numberOfMeldinger = 1,
                    )
                    it("Returns all meldinger for personident grouped by conversationRef") {
                        with(
                            handleRequest(HttpMethod.Get, apiUrl) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val respons = objectMapper.readValue<MeldingResponseDTO>(response.content!!)
                            respons.conversations[firstConversation]?.size shouldBeEqualTo 4
                            respons.conversations[secondConversation]?.size shouldBeEqualTo 1

                            val pMeldinger = database.getMeldingerForArbeidstaker(personIdent)
                            pMeldinger.size shouldBeEqualTo 5
                            pMeldinger.first().arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
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

                        val producerRecordSlot = slot<ProducerRecord<String, DialogmeldingBestillingDTO>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val dialogmeldingBestillingDTO = producerRecordSlot.captured.value()
                        dialogmeldingBestillingDTO.behandlerRef shouldBeEqualTo meldingTilBehandlerDTO.behandlerRef.toString()
                        dialogmeldingBestillingDTO.dialogmeldingTekst shouldBeEqualTo meldingTilBehandlerDTO.tekst
                        dialogmeldingBestillingDTO.dialogmeldingType shouldBeEqualTo DialogmeldingType.DIALOG_FORESPORSEL.name
                        dialogmeldingBestillingDTO.dialogmeldingKode shouldBeEqualTo DialogmeldingKode.FORESPORSEL.value
                        dialogmeldingBestillingDTO.dialogmeldingKodeverk shouldBeEqualTo DialogmeldingKodeverk.FORESPORSEL.name
                        dialogmeldingBestillingDTO.dialogmeldingUuid shouldBeEqualTo pMelding.uuid.toString()
                        dialogmeldingBestillingDTO.personIdent shouldBeEqualTo pMelding.arbeidstakerPersonIdent
                        dialogmeldingBestillingDTO.dialogmeldingRefConversation shouldBeEqualTo pMelding.conversationRef.toString()
                        dialogmeldingBestillingDTO.dialogmeldingRefParent shouldBeEqualTo null
                        dialogmeldingBestillingDTO.dialogmeldingVedlegg shouldBeEqualTo null
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
