package no.nav.syfo.melding.kafka

import com.google.cloud.storage.Blob
import com.google.cloud.storage.Storage
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.melding.kafka.legeerklaring.KafkaLegeerklaringConsumer
import no.nav.syfo.melding.kafka.legeerklaring.LEGEERKLARING_TOPIC
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
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
        val storage = mockk<Storage>()
        val blob = mockk<Blob>()
        val bucketName = externalMockEnvironment.environment.legeerklaringBucketName
        val bucketNameVedlegg = externalMockEnvironment.environment.legeerklaringVedleggBucketName

        val kafkaLegeerklaringConsumer = KafkaLegeerklaringConsumer(
            database = database,
            storage = storage,
            bucketName = bucketName,
            bucketNameVedlegg = bucketNameVedlegg,
        )

        describe("Read legeerklaring sent from behandler to NAV from Kafka Topic") {
            describe("Happy path") {
                it("Should not store legerklaring when no melding sendt (unknown conversationRef)") {
                    val legeerklaring = generateKafkaLegeerklaringFraBehandlerDTO(
                        behandlerPersonIdent = behandlerPersonIdent,
                        behandlerNavn = behandlerNavn,
                        personIdent = personIdent,
                    )
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = emptyList(),
                    )
                    every { blob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaring)
                    every { storage.get(bucketName, legeerklaring.msgId) } returns blob
                    val mockConsumer = mockKafkaConsumer(kafkaLegeerklaring, LEGEERKLARING_TOPIC)

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
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = emptyList(),
                    )
                    every { blob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaring)
                    every { storage.get(bucketName, legeerklaring.msgId) } returns blob
                    val mockConsumer = mockKafkaConsumer(kafkaLegeerklaring, LEGEERKLARING_TOPIC)

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
                    val meldingTilBehandler = defaultMeldingTilBehandler
                    val (conversationRef, _) = database.createMeldingerTilBehandler(
                        meldingTilBehandler.copy(
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
                        parentRef = null,
                    )
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = emptyList(),
                    )
                    every { blob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaring)
                    every { storage.get(bucketName, legeerklaring.msgId) } returns blob
                    val mockConsumer = mockKafkaConsumer(kafkaLegeerklaring, LEGEERKLARING_TOPIC)

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
                    pSvar.parentRef shouldBeEqualTo meldingTilBehandler.uuid
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }
                it("Should store legeerklaring when melding recently sent to behandler") {
                    val msgId = UUID.randomUUID().toString()
                    val meldingTilBehandler = defaultMeldingTilBehandler
                    val (conversationRef, _) = database.createMeldingerTilBehandler(
                        meldingTilBehandler.copy(
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
                        parentRef = null,
                    )
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = emptyList(),
                    )
                    every { blob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaring)
                    every { storage.get(bucketName, legeerklaring.msgId) } returns blob
                    val mockConsumer = mockKafkaConsumer(kafkaLegeerklaring, LEGEERKLARING_TOPIC)

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
                    pSvar.parentRef shouldBeEqualTo meldingTilBehandler.uuid
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg shouldBe null
                }

                it("Should store legeerklaring when melding sent with same conversationRef and includes parentRef") {
                    val msgId = UUID.randomUUID().toString()
                    val meldingTilBehandler = defaultMeldingTilBehandler
                    val (conversationRef, _) = database.createMeldingerTilBehandler(
                        meldingTilBehandler.copy(
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
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = emptyList(),
                    )
                    every { blob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaring)
                    every { storage.get(bucketName, legeerklaring.msgId) } returns blob
                    val mockConsumer = mockKafkaConsumer(kafkaLegeerklaring, LEGEERKLARING_TOPIC)

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
                    pSvar.parentRef.toString() shouldBeEqualTo legeerklaring.conversationRef?.refToParent
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
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = emptyList(),
                    )
                    every { blob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaring)
                    every { storage.get(bucketName, legeerklaring.msgId) } returns blob
                    val mockConsumer = mockKafkaConsumer(kafkaLegeerklaring, LEGEERKLARING_TOPIC)

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
