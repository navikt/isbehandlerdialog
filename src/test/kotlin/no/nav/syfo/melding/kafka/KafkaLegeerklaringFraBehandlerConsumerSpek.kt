package no.nav.syfo.melding.kafka

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.legeerklaring.KafkaLegeerklaringConsumer
import no.nav.syfo.melding.kafka.legeerklaring.LEGEERKLARING_TOPIC
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class KafkaLegeerklaringFraBehandlerConsumerSpek : Spek({

    with(TestApplicationEngine()) {
        start()
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
        val behandlerPersonIdent = UserConstants.BEHANDLER_PERSONIDENT
        val behandlerNavn = UserConstants.BEHANDLER_NAVN

        afterEachTest {
            database.dropData()
        }

        val kafkaLegeerklaringConsumer = KafkaLegeerklaringConsumer(
            database = database,
        )

        describe("Read legeerklaring sent from behandler to NAV from Kafka Topic") {
            describe("Happy path") {
                it("Should not store legerklaring when no melding sendt (unknown conversationRef)") {
                    val legeerklaring = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = behandlerPersonIdent,
                        behandlerNavn = behandlerNavn,
                        personIdent = personIdent,
                    )
                    val mockConsumer = mockKafkaConsumer(legeerklaring, LEGEERKLARING_TOPIC)

                    runBlocking {
                        kafkaLegeerklaringConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 0
                }
                it("Should not store legerklaring when no melding sendt and no conversationRef") {
                    val legeerklaring = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = behandlerPersonIdent,
                        behandlerNavn = behandlerNavn,
                        personIdent = personIdent,
                        conversationRef = null,
                    )
                    val mockConsumer = mockKafkaConsumer(legeerklaring, LEGEERKLARING_TOPIC)

                    runBlocking {
                        kafkaLegeerklaringConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    database.getMeldingerForArbeidstaker(personIdent).size shouldBeEqualTo 0
                }
                it("Should store legeerklaring when melding sent with same conversationRef") {
                    val msgId = UUID.randomUUID().toString()
                    val (conversationRef, _) = database.createMeldingerTilBehandler(
                        defaultMeldingTilBehandler.copy(
                            arbeidstakerPersonIdent = personIdent,
                            behandlerPersonIdent = behandlerPersonIdent,
                            behandlerNavn = behandlerNavn,
                            type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
                        )
                    )
                    val legeerklaring = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = behandlerPersonIdent,
                        behandlerNavn = behandlerNavn,
                        personIdent = personIdent,
                        msgId = msgId,
                        conversationRef = conversationRef.toString(),
                    )
                    val mockConsumer = mockKafkaConsumer(legeerklaring, LEGEERKLARING_TOPIC)

                    runBlocking {
                        kafkaLegeerklaringConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId
                    pSvar.behandlerPersonIdent shouldBeEqualTo behandlerPersonIdent.value
                    pSvar.behandlerNavn shouldBeEqualTo UserConstants.BEHANDLER_NAVN
                    pSvar.antallVedlegg shouldBeEqualTo 0
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    pSvar.conversationRef shouldBeEqualTo conversationRef
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }
                it("Should store legeerklaring when melding recently sent to behandler") {
                    val msgId = UUID.randomUUID().toString()
                    val (conversationRef, _) = database.createMeldingerTilBehandler(
                        defaultMeldingTilBehandler.copy(
                            arbeidstakerPersonIdent = personIdent,
                            behandlerPersonIdent = behandlerPersonIdent,
                            behandlerNavn = behandlerNavn,
                            type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
                        )
                    )
                    val legeerklaring = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = behandlerPersonIdent,
                        behandlerNavn = behandlerNavn,
                        personIdent = personIdent,
                        msgId = msgId,
                        conversationRef = null,
                    )
                    val mockConsumer = mockKafkaConsumer(legeerklaring, LEGEERKLARING_TOPIC)

                    runBlocking {
                        kafkaLegeerklaringConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 2
                    val pSvar = pMeldingListAfter.last()
                    pSvar.arbeidstakerPersonIdent shouldBeEqualTo personIdent.value
                    pSvar.innkommende shouldBe true
                    pSvar.msgId shouldBeEqualTo msgId
                    pSvar.behandlerPersonIdent shouldBeEqualTo behandlerPersonIdent.value
                    pSvar.behandlerNavn shouldBeEqualTo UserConstants.BEHANDLER_NAVN
                    pSvar.antallVedlegg shouldBeEqualTo 0
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    pSvar.conversationRef shouldBeEqualTo conversationRef
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }
                it("Should not store legeerklaring when melding sent to behandler long time ago") {
                    val msgId = UUID.randomUUID().toString()
                    database.createMeldingerTilBehandler(
                        defaultMeldingTilBehandler.copy(
                            arbeidstakerPersonIdent = personIdent,
                            behandlerPersonIdent = behandlerPersonIdent,
                            behandlerNavn = behandlerNavn,
                            type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
                            tidspunkt = OffsetDateTime.now().minusYears(1),
                        )
                    )
                    val legeerklaring = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = behandlerPersonIdent,
                        behandlerNavn = behandlerNavn,
                        personIdent = personIdent,
                        msgId = msgId,
                        conversationRef = null,
                    )
                    val mockConsumer = mockKafkaConsumer(legeerklaring, LEGEERKLARING_TOPIC)

                    runBlocking {
                        kafkaLegeerklaringConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 1
                }
                it("Should not store legeerklaring sent before melding to behandler") {
                    val msgId = UUID.randomUUID().toString()
                    database.createMeldingerTilBehandler(
                        defaultMeldingTilBehandler.copy(
                            arbeidstakerPersonIdent = personIdent,
                            behandlerPersonIdent = behandlerPersonIdent,
                            behandlerNavn = behandlerNavn,
                            type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING,
                        )
                    )
                    val legeerklaring = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = behandlerPersonIdent,
                        behandlerNavn = behandlerNavn,
                        personIdent = personIdent,
                        msgId = msgId,
                        conversationRef = null,
                        tidspunkt = LocalDateTime.now().minusDays(7),
                    )
                    val mockConsumer = mockKafkaConsumer(legeerklaring, LEGEERKLARING_TOPIC)

                    runBlocking {
                        kafkaLegeerklaringConsumer.pollAndProcessRecords(
                            kafkaConsumer = mockConsumer,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }

                    val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
                    pMeldingListAfter.size shouldBeEqualTo 1
                }
            }
        }
    }
})
