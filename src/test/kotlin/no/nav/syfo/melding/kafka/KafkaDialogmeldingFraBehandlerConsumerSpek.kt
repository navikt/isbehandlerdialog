package no.nav.syfo.melding.kafka

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.melding.api.toMeldingTilBehandler
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.kafka.domain.DialogmeldingType
import no.nav.syfo.melding.kafka.domain.KafkaDialogmeldingFraBehandlerDTO
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateDialogmeldingFraBehandlerDTO
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandlerRequestDTO
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.*

class KafkaDialogmeldingFraBehandlerSpek : Spek({

    with(TestApplicationEngine()) {
        start()
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val azureAdClient = AzureAdClient(
            azureEnvironment = externalMockEnvironment.environment.azure,
        )
        val padm2Client = Padm2Client(
            azureAdClient = azureAdClient,
            clientEnvironment = externalMockEnvironment.environment.clients.padm2,
        )

        afterEachTest {
            database.dropData()
        }

        val kafkaDialogmeldingFraBehandlerConsumer = KafkaDialogmeldingFraBehandlerConsumer(
            database = database,
            padm2Client = padm2Client,
        )

        describe("Read dialogmelding sent from behandler to NAV from Kafka Topic") {
            describe("Happy path") {
                it("Receive dialogmeldinger") {
                    val dialogmelding = generateDialogmeldingFraBehandlerDTO(
                        uuid = UUID.randomUUID(),
                        msgType = DialogmeldingType.DIALOG_FORESPORSEL.name,
                    )
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmelding)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT).size shouldBeEqualTo 0
                }
                it("Receive dialogmelding DIALOG_SVAR but unknown conversationRef") {
                    val dialogmelding = generateDialogmeldingFraBehandlerDTO(
                        uuid = UUID.randomUUID(),
                        msgType = DialogmeldingType.DIALOG_SVAR.name,
                    )
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmelding)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT).size shouldBeEqualTo 0
                }
                it("Receive dialogmelding DIALOG_SVAR and known conversationRef") {
                    val conversationRef = database.createMeldingerTilBehandler(
                        generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(
                            personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        )
                    )
                    val msgId = UUID.randomUUID()
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerDTO(
                        uuid = msgId,
                        msgType = DialogmeldingType.DIALOG_SVAR.name,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmeldingInnkommet)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId.toString()
                    pSvar.behandlerPersonIdent shouldBeEqualTo UserConstants.BEHANDLER_PERSONIDENT.value
                    pSvar.behandlerNavn shouldBeEqualTo UserConstants.BEHANDLER_NAVN
                    pSvar.antallVedlegg shouldBeEqualTo 0
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }
                it("Receive dialogmelding DIALOG_SVAR and known conversationRef and with vedlegg") {
                    val conversationRef = database.createMeldingerTilBehandler(
                        generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(
                            personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        )
                    )
                    val msgId = UserConstants.MSG_ID_WITH_VEDLEGG
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerDTO(
                        uuid = msgId,
                        msgType = DialogmeldingType.DIALOG_SVAR.name,
                        conversationRef = conversationRef.toString(),
                        antallVedlegg = 1,
                    )
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmeldingInnkommet)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo UserConstants.MSG_ID_WITH_VEDLEGG.toString()
                    pSvar.antallVedlegg shouldBeEqualTo 1
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg!!.pdf shouldBeEqualTo UserConstants.VEDLEGG_BYTEARRAY
                }
                it("Receive dialogmelding DIALOG_SVAR for dialogmøte") {
                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerDTO(
                        uuid = UUID.randomUUID(),
                        msgType = DialogmeldingType.DIALOG_SVAR.name,
                        conversationRef = UUID.randomUUID().toString(),
                        kodeverk = "2.16.578.1.12.4.1.1.8126",
                        kodeTekst = "Ja, jeg kommer",
                        kode = "1",
                    )
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmeldingInnkommet)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }
                    database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT).size shouldBeEqualTo 0
                }
                it("Receive duplicate dialogmelding DIALOG_SVAR and known conversationRef") {
                    val conversationRef = database.createMeldingerTilBehandler(
                        generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(
                            personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        )
                    )

                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerDTO(
                        uuid = UUID.randomUUID(),
                        msgType = DialogmeldingType.DIALOG_SVAR.name,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmeldingInnkommet)

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

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    pMeldingListAfter.size shouldBeEqualTo 2
                }
                it("Receive two dialogmelding DIALOG_SVAR and known conversationRef") {
                    val conversationRef = database.createMeldingerTilBehandler(
                        generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(
                            personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        )
                    )

                    val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerDTO(
                        uuid = UUID.randomUUID(),
                        msgType = DialogmeldingType.DIALOG_SVAR.name,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmeldingInnkommet)

                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }
                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val dialogmeldingInnkommetAgain = generateDialogmeldingFraBehandlerDTO(
                        uuid = UUID.randomUUID(),
                        msgType = DialogmeldingType.DIALOG_SVAR.name,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumerAgain = mockKafkaConsumerWithDialogmelding(dialogmeldingInnkommetAgain)
                    runBlocking {
                        kafkaDialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumerAgain,
                        )
                    }
                    verify(exactly = 1) { mockConsumerAgain.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    pMeldingListAfter.size shouldBeEqualTo 3
                }
            }
        }
    }
})

fun mockKafkaConsumerWithDialogmelding(dialogmelding: KafkaDialogmeldingFraBehandlerDTO): KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO> {
    val partition = 0
    val dialogmeldingTopicPartition = TopicPartition(
        DIALOGMELDING_FROM_BEHANDLER_TOPIC,
        partition,
    )

    val dialogmeldingRecord = ConsumerRecord(
        DIALOGMELDING_FROM_BEHANDLER_TOPIC,
        partition,
        1,
        dialogmelding.msgId,
        dialogmelding,
    )

    val mockConsumer = mockk<KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>>()
    every { mockConsumer.poll(any<Duration>()) } returns ConsumerRecords(
        mapOf(
            dialogmeldingTopicPartition to listOf(
                dialogmeldingRecord,
            )
        )
    )
    every { mockConsumer.commitSync() } returns Unit

    return mockConsumer
}
