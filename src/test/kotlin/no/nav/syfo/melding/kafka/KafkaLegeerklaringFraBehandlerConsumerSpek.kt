package no.nav.syfo.melding.kafka

import com.google.cloud.storage.Blob
import com.google.cloud.storage.Storage
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.melding.kafka.legeerklaring.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

private const val expectedMeldingTekst = "Mottatt legeerklæring"

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
        val vedleggBlob = mockk<Blob>()
        val vedleggPdf = byteArrayOf(0x2E, 0x28)

        val bucketName = externalMockEnvironment.environment.legeerklaringBucketName
        val bucketNameVedlegg = externalMockEnvironment.environment.legeerklaringVedleggBucketName
        val pdfgenClient = PdfGenClient(
            pdfGenBaseUrl = externalMockEnvironment.environment.clients.dialogmeldingpdfgen.baseUrl,
            legeerklaringPdfGenBaseUrl = externalMockEnvironment.environment.clients.dialogmeldingpdfgen.baseUrl,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
        val kafkaLegeerklaringConsumer = KafkaLegeerklaringConsumer(
            database = database,
            storage = storage,
            bucketName = bucketName,
            bucketNameVedlegg = bucketNameVedlegg,
            pdfgenClient = pdfgenClient,
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
                    pSvar.antallVedlegg shouldBeEqualTo 1
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    pSvar.conversationRef shouldBeEqualTo conversationRef
                    pSvar.parentRef shouldBeEqualTo meldingTilBehandler.uuid
                    pSvar.tekst shouldBeEqualTo expectedMeldingTekst
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg?.pdf shouldBeEqualTo UserConstants.PDF_LEGEERKLARING
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
                    pSvar.antallVedlegg shouldBeEqualTo 1
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    pSvar.conversationRef shouldBeEqualTo conversationRef
                    pSvar.parentRef shouldBeEqualTo meldingTilBehandler.uuid
                    pSvar.tekst shouldBeEqualTo expectedMeldingTekst
                    val vedlegg = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg?.pdf shouldBeEqualTo UserConstants.PDF_LEGEERKLARING
                }

                it("Should store legeerklaring with vedlegg when melding recently sent to behandler") {
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
                    val vedleggId = UUID.randomUUID().toString()
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = listOf(vedleggId),
                    )
                    every { blob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaring)
                    every { storage.get(bucketName, legeerklaring.msgId) } returns blob
                    val mockConsumer = mockKafkaConsumer(kafkaLegeerklaring, LEGEERKLARING_TOPIC)
                    val legeerklaringVedlegg = LegeerklaringVedleggDTO(
                        vedlegg = Vedlegg(
                            content = Content(
                                contentType = "application/pdf",
                                content = String(Base64.getMimeEncoder().encode(vedleggPdf)),
                            ),
                            type = "application/pdf",
                            description = "",
                        ),
                    )
                    every { vedleggBlob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaringVedlegg)
                    every { storage.get(bucketNameVedlegg, vedleggId) } returns vedleggBlob

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
                    pSvar.antallVedlegg shouldBeEqualTo 2
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    pSvar.conversationRef shouldBeEqualTo conversationRef
                    pSvar.parentRef shouldBeEqualTo meldingTilBehandler.uuid
                    pSvar.tekst shouldBeEqualTo expectedMeldingTekst
                    val vedlegg1 = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg1?.pdf shouldBeEqualTo UserConstants.PDF_LEGEERKLARING
                    val vedlegg2 = database.getVedlegg(pSvar.uuid, 1)
                    vedlegg2?.pdf shouldBeEqualTo vedleggPdf
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
                    val vedleggId = UUID.randomUUID().toString()
                    val kafkaLegeerklaring = KafkaLegeerklaeringMessage(
                        legeerklaeringObjectId = legeerklaring.msgId,
                        validationResult = ValidationResult(Status.OK),
                        vedlegg = listOf(vedleggId),
                    )
                    val legeerklaringVedlegg = LegeerklaringVedleggDTO(
                        vedlegg = Vedlegg(
                            content = Content(
                                contentType = "application/pdf",
                                content = String(Base64.getMimeEncoder().encode(vedleggPdf)),
                            ),
                            type = "application/pdf",
                            description = "",
                        ),
                    )
                    every { vedleggBlob.getContent() } returns configuredJacksonMapper().writeValueAsBytes(legeerklaringVedlegg)
                    every { storage.get(bucketNameVedlegg, vedleggId) } returns vedleggBlob
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
                    pSvar.antallVedlegg shouldBeEqualTo 2
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    pSvar.conversationRef shouldBeEqualTo conversationRef
                    pSvar.parentRef.toString() shouldBeEqualTo legeerklaring.conversationRef?.refToParent
                    pSvar.tekst shouldBeEqualTo expectedMeldingTekst
                    val vedlegg1 = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg1?.pdf shouldBeEqualTo UserConstants.PDF_LEGEERKLARING
                    val vedlegg2 = database.getVedlegg(pSvar.uuid, 1)
                    vedlegg2?.pdf shouldBeEqualTo vedleggPdf
                }

                it("Should store legeerklaring with vedlegg when melding sent with same conversationRef and includes parentRef") {
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
                    pSvar.antallVedlegg shouldBeEqualTo 1
                    pSvar.veilederIdent shouldBeEqualTo null
                    pSvar.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                    pSvar.conversationRef shouldBeEqualTo conversationRef
                    pSvar.parentRef.toString() shouldBeEqualTo legeerklaring.conversationRef?.refToParent
                    pSvar.tekst shouldBeEqualTo expectedMeldingTekst
                    val vedlegg1 = database.getVedlegg(pSvar.uuid, 0)
                    vedlegg1?.pdf shouldBeEqualTo UserConstants.PDF_LEGEERKLARING
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
