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
                it("Receive legerklaring") {
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
                it("Receive legeerklaring and known conversationRef") {
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
                    pSvar.msgId shouldBeEqualTo msgId.toString()
                    pSvar.behandlerPersonIdent shouldBeEqualTo behandlerPersonIdent.value
                    pSvar.behandlerNavn shouldBeEqualTo UserConstants.BEHANDLER_NAVN
                    pSvar.antallVedlegg shouldBeEqualTo 0
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }
            }
        }
    }
})
