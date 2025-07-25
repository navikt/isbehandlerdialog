package no.nav.syfo.melding.kafka

import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.MeldingTilBehandler
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.padm2.Padm2Client
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.database.getVedlegg
import no.nav.syfo.infrastructure.kafka.dialogmelding.DIALOGMELDING_FROM_BEHANDLER_TOPIC
import no.nav.syfo.infrastructure.kafka.dialogmelding.KafkaDialogmeldingFraBehandlerConsumer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createMeldingerTilBehandler
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNullOrEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class KafkaDialogmeldingFraBehandlerConsumerSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val mockHttpClient = externalMockEnvironment.mockHttpClient
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = mockHttpClient,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.oppfolgingstilfelle,
        httpClient = mockHttpClient,
    )
    val padm2Client = Padm2Client(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.padm2,
        httpClient = mockHttpClient,
    )

    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT

    afterEachTest {
        database.dropData()
    }

    val kafkaDialogmeldingFraBehandlerConsumer = KafkaDialogmeldingFraBehandlerConsumer(
        database = database,
        padm2Client = padm2Client,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )

    describe("Read dialogmelding sent from behandler to NAV from Kafka Topic") {
        describe("Happy path") {
            it("Receive dialogmeldinger creates no meldinger") {
                val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO()
                val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }

                verify(exactly = 1) { mockConsumer.commitSync() }

                database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 0
            }
            it("Receive dialogmelding DIALOG_SVAR but unknown conversationRef creates no melding") {
                val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO()
                val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }

                verify(exactly = 1) { mockConsumer.commitSync() }

                database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 0
            }
            it("Receive dialogmelding DIALOG_SVAR with empty string as conversationRef doesn't fail") {
                val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO().copy(
                    conversationRef = "",
                )
                val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }
            }
            it("Receive dialogmelding DIALOG_SVAR with empty string as parentRef doesn't fail") {
                val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO().copy(
                    parentRef = "",
                )
                val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }
            }
            it("Receive dialogmelding DIALOG_NOTAT from behandler creates melding when person sykmeldt") {
                val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatDTO()
                val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }

                verify(exactly = 1) { mockConsumer.commitSync() }

                database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 1
            }
            it("Receive dialogmelding DIALOG_NOTAT from behandler creates no melding when person not sykmeldt") {
                val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatDTO(
                    personIdent = UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS,
                )
                val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }

                verify(exactly = 1) { mockConsumer.commitSync() }

                database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 0
            }
            it("Receive dialogmelding DIALOG_NOTAT from behandler creates no melding when person sykmeldt long time ago") {
                val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatDTO(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT_INACTIVE,
                )
                val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }

                verify(exactly = 1) { mockConsumer.commitSync() }

                database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 0
            }
            describe("Having sent melding til behandler FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER") {
                it("Receive dialogmelding DIALOG_SVAR and known conversationRef creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER") {
                    val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                    val msgId = UUID.randomUUID()
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        uuid = msgId,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId.toString()
                    pSvar.behandlerPersonIdent shouldBeEqualTo UserConstants.BEHANDLER_PERSONIDENT.value
                    pSvar.behandlerNavn shouldBeEqualTo UserConstants.BEHANDLER_NAVN
                    pSvar.antallVedlegg shouldBeEqualTo 0
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }
                it("Receive dialogmelding DIALOG_SVAR and known conversationRef matching uuid of sent message creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER") {
                    database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                    val msgId = UUID.randomUUID()
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        uuid = msgId,
                        conversationRef = defaultMeldingTilBehandler.uuid.toString(),
                        parentRef = UUID.randomUUID().toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pMelding = pMeldingListAfter.first()
                    val pSvar = pMeldingListAfter.last()
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId.toString()
                    pSvar.conversationRef shouldBeEqualTo pMelding.conversationRef
                }
                it("Receive dialogmelding DIALOG_SVAR and parentRef matching uuid of sent message creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER") {
                    database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                    val msgId = UUID.randomUUID()
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        uuid = msgId,
                        conversationRef = UUID.randomUUID().toString(),
                        parentRef = defaultMeldingTilBehandler.uuid.toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pMelding = pMeldingListAfter.first()
                    val pSvar = pMeldingListAfter.last()
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId.toString()
                    pSvar.conversationRef shouldBeEqualTo pMelding.conversationRef
                }
                it("Receive dialogmelding DIALOG_SVAR and known conversationRef and with vedlegg creates melding with vedlegg") {
                    val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                    val msgId = UserConstants.MSG_ID_WITH_VEDLEGG
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        uuid = msgId,
                        conversationRef = conversationRef.toString(),
                        antallVedlegg = 1,
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo UserConstants.MSG_ID_WITH_VEDLEGG.toString()
                    pSvar.antallVedlegg shouldBeEqualTo 1
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg!!.pdf shouldBeEqualTo UserConstants.VEDLEGG_BYTEARRAY
                }
                it("Receive duplicate dialogmelding DIALOG_SVAR and known conversationRef creates no duplicate melding fra behandler") {
                    val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)

                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }
                    verify(exactly = 1) { mockConsumer.commitSync() }
                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }
                    verify(exactly = 2) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                }
                it("Receive two dialogmelding DIALOG_SVAR and known conversationRef creates two melding fra behandler") {
                    val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)

                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }
                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val dialogmeldingInnkommetAgain = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumerAgain =
                        mockKafkaConsumer(dialogmeldingInnkommetAgain, DIALOGMELDING_FROM_BEHANDLER_TOPIC)
                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumerAgain,
                        )
                    }
                    verify(exactly = 1) { mockConsumerAgain.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 3
                }
                it("Receive DIALOG_SVAR and unknown conversationref creates no MeldingFraBehandler") {
                    database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                    val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO()
                    val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 1
                }
                it("Receive DIALOG_NOTAT and known conversationref creates MeldingFraBehandler") {
                    val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                    val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatDTO(conversationRef = conversationRef.toString())
                    val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2

                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name
                }
            }
            describe("Having sent melding til behandler FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER and PAMINNELSE") {
                it("Receive dialogmelding DIALOG_SVAR and known conversationRef creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER") {
                    val meldingTilBehandler = defaultMeldingTilBehandler
                    val (conversationRef, _) = database.createMeldingerTilBehandler(meldingTilBehandler)
                    database.createMeldingerTilBehandler(
                        MeldingTilBehandler.createForesporselPasientPaminnelse(
                            opprinneligMelding = meldingTilBehandler,
                            veilederIdent = UserConstants.VEILEDER_IDENT,
                            document = emptyList(),
                        )
                    )

                    val msgId = UUID.randomUUID()
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                        uuid = msgId,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 3
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId.toString()
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name
                }
            }
            describe("Having sent melding til behandler HENVENDELSE_MELDING_FRA_NAV") {
                it("Receive dialogmelding DIALOG_NOTAT and known conversationRef creates MeldingFraBehandler with type HENVENDELSE_MELDING_FRA_NAV") {
                    val (conversationRef, _) = database.createMeldingerTilBehandler(generateMeldingTilBehandler(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV))
                    val msgId = UUID.randomUUID()
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerDialogNotatDTO(
                        uuid = msgId,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId.toString()
                    pSvar.behandlerPersonIdent shouldBeEqualTo UserConstants.BEHANDLER_PERSONIDENT.value
                    pSvar.behandlerNavn shouldBeEqualTo UserConstants.BEHANDLER_NAVN
                    pSvar.antallVedlegg shouldBeEqualTo 0
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.HENVENDELSE_MELDING_FRA_NAV.name
                    pSvar.tekst.shouldNotBeNullOrEmpty()
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }
                it("Receive DIALOG_NOTAT and unknown conversationref creates no MeldingFraBehandler") {
                    database.createMeldingerTilBehandler(generateMeldingTilBehandler(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV))
                    val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatIkkeSykefravrDTO()
                    val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 1
                }

                it("Receive DIALOG_SVAR and known conversationref creates no MeldingFraBehandler") {
                    val (conversationRef, _) = database.createMeldingerTilBehandler(generateMeldingTilBehandler(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV))
                    val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO(conversationRef = conversationRef.toString())
                    val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2

                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.type shouldBeEqualTo MeldingType.HENVENDELSE_MELDING_FRA_NAV.name
                }
            }
            it("Receive dialogmelding DIALOG_SVAR for dialogmøte creates no melding") {
                val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                    kodeverk = "2.16.578.1.12.4.1.1.8126",
                    kodeTekst = "Ja, jeg kommer",
                    kode = "1",
                )
                val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                runBlocking {
                    kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                        kafkaConsumer = mockConsumer,
                    )
                }

                verify(exactly = 1) { mockConsumer.commitSync() }
                database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 0
            }
        }
    }
})
