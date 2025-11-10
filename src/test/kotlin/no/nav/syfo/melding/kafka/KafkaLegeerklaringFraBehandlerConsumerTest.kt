package no.nav.syfo.melding.kafka

import com.google.cloud.storage.Blob
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.database.getVedlegg
import no.nav.syfo.infrastructure.kafka.domain.KafkaLegeerklaeringMessage
import no.nav.syfo.infrastructure.kafka.domain.Status
import no.nav.syfo.infrastructure.kafka.domain.ValidationResult
import no.nav.syfo.infrastructure.kafka.legeerklaring.*
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createMeldingerTilBehandler
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateKafkaLegeerklaringFraBehandlerDTO
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import no.nav.syfo.util.configuredJacksonMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

private const val expectedMeldingTekst = "Mottatt legeerklæring"

class KafkaLegeerklaringFraBehandlerConsumerTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    private val behandlerPersonIdent = UserConstants.BEHANDLER_PERSONIDENT
    private val behandlerNavn = UserConstants.BEHANDLER_NAVN
    private val storage = mockk<Storage>()
    private val blob = mockk<Blob>()
    private val vedleggBlob = mockk<Blob>()
    private val vedleggPdf = byteArrayOf(0x2E, 0x28)
    private val bucketName = externalMockEnvironment.environment.legeerklaringBucketName
    private val bucketNameVedlegg = externalMockEnvironment.environment.legeerklaringVedleggBucketName
    private val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = externalMockEnvironment.environment.clients.dialogmeldingpdfgen.baseUrl,
        legeerklaringPdfGenBaseUrl = externalMockEnvironment.environment.clients.dialogmeldingpdfgen.baseUrl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val kafkaLegeerklaringConsumer = KafkaLegeerklaringConsumer(
        database = database,
        storage = storage,
        bucketName = bucketName,
        bucketNameVedlegg = bucketNameVedlegg,
        pdfgenClient = pdfgenClient,
    )

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `Should not store legerklaring when no melding sendt (unknown conversationRef)`() = runTest {
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

        kafkaLegeerklaringConsumer.pollAndProcessRecords(
            kafkaConsumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, database.getMeldingerForArbeidstaker(personIdent).size)
    }

    @Test
    fun `Should not store legerklaring when no melding sendt and no conversationRef`() = runTest {
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

        kafkaLegeerklaringConsumer.pollAndProcessRecords(
            kafkaConsumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, database.getMeldingerForArbeidstaker(personIdent).size)
    }

    @Test
    fun `Should store legeerklaring when melding sent with same conversationRef`() = runTest {
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

        kafkaLegeerklaringConsumer.pollAndProcessRecords(
            kafkaConsumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
        assertEquals(2, pMeldingListAfter.size)
        val pSvar = pMeldingListAfter.last()
        assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
        assertTrue(pSvar.innkommende)
        assertEquals(msgId, pSvar.msgId)
        assertEquals(behandlerPersonIdent.value, pSvar.behandlerPersonIdent)
        assertEquals(UserConstants.BEHANDLER_NAVN, pSvar.behandlerNavn)
        assertEquals(1, pSvar.antallVedlegg)
        assertNull(pSvar.veilederIdent)
        assertEquals(MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name, pSvar.type)
        assertEquals(conversationRef, pSvar.conversationRef)
        assertEquals(meldingTilBehandler.uuid, pSvar.parentRef)
        assertEquals(expectedMeldingTekst, pSvar.tekst)
        val vedlegg = database.getVedlegg(pSvar.uuid, 0)
        assertArrayEquals(UserConstants.PDF_LEGEERKLARING, vedlegg!!.pdf)
    }

    @Test
    fun `Should store legeerklaring when melding recently sent to behandler`() = runTest {
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

        kafkaLegeerklaringConsumer.pollAndProcessRecords(
            kafkaConsumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
        assertEquals(2, pMeldingListAfter.size)
        val pSvar = pMeldingListAfter.last()
        assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
        assertTrue(pSvar.innkommende)
        assertEquals(msgId, pSvar.msgId)
        assertEquals(behandlerPersonIdent.value, pSvar.behandlerPersonIdent)
        assertEquals(UserConstants.BEHANDLER_NAVN, pSvar.behandlerNavn)
        assertEquals(1, pSvar.antallVedlegg)
        assertNull(pSvar.veilederIdent)
        assertEquals(MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name, pSvar.type)
        assertEquals(conversationRef, pSvar.conversationRef)
        assertEquals(meldingTilBehandler.uuid, pSvar.parentRef)
        assertEquals(expectedMeldingTekst, pSvar.tekst)
        val vedlegg = database.getVedlegg(pSvar.uuid, 0)
        assertArrayEquals(UserConstants.PDF_LEGEERKLARING, vedlegg!!.pdf)
    }

    @Test
    fun `Should store legeerklaring with vedlegg when melding recently sent to behandler`() = runTest {
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

        kafkaLegeerklaringConsumer.pollAndProcessRecords(
            kafkaConsumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
        assertEquals(2, pMeldingListAfter.size)
        val pSvar = pMeldingListAfter.last()
        assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
        assertTrue(pSvar.innkommende)
        assertEquals(msgId, pSvar.msgId)
        assertEquals(behandlerPersonIdent.value, pSvar.behandlerPersonIdent)
        assertEquals(UserConstants.BEHANDLER_NAVN, pSvar.behandlerNavn)
        assertEquals(2, pSvar.antallVedlegg)
        assertNull(pSvar.veilederIdent)
        assertEquals(MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name, pSvar.type)
        assertEquals(conversationRef, pSvar.conversationRef)
        assertEquals(meldingTilBehandler.uuid, pSvar.parentRef)
        assertEquals(expectedMeldingTekst, pSvar.tekst)
        val vedlegg1 = database.getVedlegg(pSvar.uuid, 0)
        assertArrayEquals(UserConstants.PDF_LEGEERKLARING, vedlegg1!!.pdf)
        val vedlegg2 = database.getVedlegg(pSvar.uuid, 1)
        assertArrayEquals(vedleggPdf, vedlegg2!!.pdf)
    }

    @Test
    fun `Should store legeerklaring when melding sent with same conversationRef and includes parentRef`() = runTest {
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

        kafkaLegeerklaringConsumer.pollAndProcessRecords(
            kafkaConsumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
        assertEquals(2, pMeldingListAfter.size)
        val pSvar = pMeldingListAfter.last()
        assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
        assertTrue(pSvar.innkommende)
        assertEquals(msgId, pSvar.msgId)
        assertEquals(behandlerPersonIdent.value, pSvar.behandlerPersonIdent)
        assertEquals(UserConstants.BEHANDLER_NAVN, pSvar.behandlerNavn)
        assertEquals(2, pSvar.antallVedlegg)
        assertNull(pSvar.veilederIdent)
        assertEquals(MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name, pSvar.type)
        assertEquals(conversationRef, pSvar.conversationRef)
        assertEquals(legeerklaring.conversationRef?.refToParent, pSvar.parentRef.toString())
        assertEquals(expectedMeldingTekst, pSvar.tekst)
        val vedlegg1 = database.getVedlegg(pSvar.uuid, 0)
        assertArrayEquals(UserConstants.PDF_LEGEERKLARING, vedlegg1!!.pdf)
        val vedlegg2 = database.getVedlegg(pSvar.uuid, 1)
        assertArrayEquals(vedleggPdf, vedlegg2!!.pdf)
    }

    @Test
    fun `Should store legeerklaring with vedlegg when melding sent with same conversationRef and includes parentRef`() =
        runTest {
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

            kafkaLegeerklaringConsumer.pollAndProcessRecords(
                kafkaConsumer = mockConsumer,
            )

            verify(exactly = 1) { mockConsumer.commitSync() }

            val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
            assertEquals(2, pMeldingListAfter.size)
            val pSvar = pMeldingListAfter.last()
            assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
            assertTrue(pSvar.innkommende)
            assertEquals(msgId, pSvar.msgId)
            assertEquals(behandlerPersonIdent.value, pSvar.behandlerPersonIdent)
            assertEquals(UserConstants.BEHANDLER_NAVN, pSvar.behandlerNavn)
            assertEquals(1, pSvar.antallVedlegg)
            assertNull(pSvar.veilederIdent)
            assertEquals(MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name, pSvar.type)
            assertEquals(conversationRef, pSvar.conversationRef)
            assertEquals(legeerklaring.conversationRef?.refToParent, pSvar.parentRef.toString())
            assertEquals(expectedMeldingTekst, pSvar.tekst)
            val vedlegg1 = database.getVedlegg(pSvar.uuid, 0)
            assertArrayEquals(UserConstants.PDF_LEGEERKLARING, vedlegg1!!.pdf)
        }

    @Test
    fun `Should not store legeerklaring when melding sent to behandler long time ago`() = runTest {
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

        kafkaLegeerklaringConsumer.pollAndProcessRecords(
            kafkaConsumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        val pMeldingListAfter = database.getMeldingerForArbeidstaker(personIdent)
        assertEquals(1, pMeldingListAfter.size)
    }
}
