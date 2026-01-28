package no.nav.syfo.melding.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.cronjob.UbesvartMeldingCronjob
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import no.nav.syfo.infrastructure.kafka.producer.KafkaUbesvartMeldingProducer
import no.nav.syfo.infrastructure.kafka.producer.PublishUbesvartMeldingService
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Future

private val threeWeeksAgo = OffsetDateTime.now().minusDays(21)

class UbesvartMeldingCronjobTest {

    private val database = ExternalMockEnvironment.instance.database
    private val meldingRepository = ExternalMockEnvironment.instance.meldingRepository
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaMeldingDTO>>()
    private val kafkaUbesvartMeldingProducer = KafkaUbesvartMeldingProducer(
        ubesvartMeldingKafkaProducer = kafkaProducer,
    )

    private val publishUbesvartMeldingService = PublishUbesvartMeldingService(
        meldingRepository = meldingRepository,
        kafkaUbesvartMeldingProducer = kafkaUbesvartMeldingProducer,
        fristHours = ExternalMockEnvironment.instance.environment.cronjobUbesvartMeldingFristHours,
    )

    private val ubesvartMeldingCronjob = UbesvartMeldingCronjob(
        publishUbesvartMeldingService = publishUbesvartMeldingService,
        intervalDelayMinutes = ExternalMockEnvironment.instance.environment.cronjobUbesvartMeldingIntervalDelayMinutes,
    )
    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT

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

    @Test
    fun `Will publish ubesvart melding til behandler foresporsel pasient when cronjob has run`() {
        val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
        val (_, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = threeWeeksAgo
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(1, result.updated)

        val melding = database.getMeldingerForArbeidstaker(personIdent).first()
        assertNotNull(melding.ubesvartPublishedAt)

        val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val kafkaMeldingDTO = producerRecordSlot.captured.value()
        assertEquals(MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name, kafkaMeldingDTO.type)
        assertEquals(personIdent.value, kafkaMeldingDTO.personIdent)
        assertEquals(melding.uuid.toString(), kafkaMeldingDTO.uuid)
    }

    @Test
    fun `Will publish all ubesvarte meldinger when cronjob has run`() {
        val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
        val (_, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = threeWeeksAgo
        )

        val meldingTilBehandlerWithOtherConversationRef = generateMeldingTilBehandler(personIdent)
        val (_, idListForMeldingWithOtherConversationRef) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandlerWithOtherConversationRef,
        )
        database.updateMeldingCreatedAt(
            id = idListForMeldingWithOtherConversationRef.first(),
            createdAt = threeWeeksAgo
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(2, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        assertNotNull(meldinger.first().ubesvartPublishedAt)
        assertNotNull(meldinger.last().ubesvartPublishedAt)

        val producerRecordSlot1 = slot<ProducerRecord<String, KafkaMeldingDTO>>()
        val producerRecordSlot2 = slot<ProducerRecord<String, KafkaMeldingDTO>>()
        verifyOrder {
            kafkaProducer.send(capture(producerRecordSlot1))
            kafkaProducer.send(capture(producerRecordSlot2))
        }

        val firstKafkaMeldingDTO = producerRecordSlot1.captured.value()
        val secondKafkaMeldingDTO = producerRecordSlot2.captured.value()
        assertEquals(meldinger.first().uuid.toString(), firstKafkaMeldingDTO.uuid)
        assertEquals(meldinger.last().uuid.toString(), secondKafkaMeldingDTO.uuid)
    }

    @Test
    fun `Will not publish any ubesvart melding when no melding older than 3 weeks`() {
        val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
        val (_, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = OffsetDateTime.now().minusDays(20)
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        assertNull(meldinger.first().ubesvartPublishedAt)

        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `Will not publish ubesvart melding when melding is besvart`() {
        val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
        val (conversationRef, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = threeWeeksAgo
        )

        val meldingFraBehandler = generateMeldingFraBehandler(
            conversationRef = conversationRef,
            personIdent = personIdent,
        )
        database.createMeldingerFraBehandler(
            meldingFraBehandler = meldingFraBehandler,
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        assertNull(meldinger.first().ubesvartPublishedAt)

        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `Will publish ubesvart melding when newest melding in conversation is ubesvart`() {
        val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
        val (conversationRef, idListUtgaende) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
            numberOfMeldinger = 2,
        )
        database.updateMeldingCreatedAt(
            id = idListUtgaende.first(),
            createdAt = OffsetDateTime.now().minusDays(40)
        )
        database.updateMeldingCreatedAt(
            id = idListUtgaende.last(),
            createdAt = threeWeeksAgo
        )

        val meldingFraBehandler = generateMeldingFraBehandler(
            conversationRef = conversationRef,
            personIdent = personIdent,
        )
        val (_, idListInnkommende) = database.createMeldingerFraBehandler(
            meldingFraBehandler = meldingFraBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idListInnkommende.first(),
            createdAt = OffsetDateTime.now().minusDays(30)
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(1, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        val utgaendeMeldinger = meldinger.filter { !it.innkommende }
        assertNull(utgaendeMeldinger.first().ubesvartPublishedAt)
        assertNotNull(utgaendeMeldinger.last().ubesvartPublishedAt)

        val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val kafkaMeldingDTO = producerRecordSlot.captured.value()
        assertEquals(MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name, kafkaMeldingDTO.type)
        assertEquals(personIdent.value, kafkaMeldingDTO.personIdent)
        assertEquals(utgaendeMeldinger.last().uuid.toString(), kafkaMeldingDTO.uuid)
    }

    @Test
    fun `Will publish ubesvart melding when melding is of type legeeklaring and cronjob has run`() {
        val meldingTilBehandler =
            generateMeldingTilBehandler(personIdent = personIdent, type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)
        val (_, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = threeWeeksAgo
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(1, result.updated)

        val melding = database.getMeldingerForArbeidstaker(personIdent).first()
        assertNotNull(melding.ubesvartPublishedAt)

        val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val kafkaMeldingDTO = producerRecordSlot.captured.value()
        assertEquals(MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name, kafkaMeldingDTO.type)
        assertEquals(personIdent.value, kafkaMeldingDTO.personIdent)
        assertEquals(melding.uuid.toString(), kafkaMeldingDTO.uuid)
    }

    @Test
    fun `Will not publish ubesvart melding when melding is of type paminnelse`() {
        val meldingTilBehandler = generateMeldingTilBehandler(
            personIdent = personIdent,
            type = MeldingType.FORESPORSEL_PASIENT_PAMINNELSE,
        )
        val (_, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = threeWeeksAgo
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        assertNull(meldinger.first().ubesvartPublishedAt)

        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `Will not publish ubesvart melding when melding is of type henvendelse melding fra NAV`() {
        val meldingTilBehandler = generateMeldingTilBehandler(
            personIdent = personIdent,
            type = MeldingType.HENVENDELSE_MELDING_FRA_NAV,
        )
        val (_, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = threeWeeksAgo
        )

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        assertNull(meldinger.first().ubesvartPublishedAt)

        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `Will not publish ubesvart melding when melding has avvist apprec status`() {
        val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
        val (_, idList) = database.createMeldingerTilBehandler(
            meldingTilBehandler = meldingTilBehandler,
        )
        database.updateMeldingCreatedAt(
            id = idList.first(),
            createdAt = threeWeeksAgo
        )
        val meldingStatus = MeldingStatus(
            uuid = UUID.randomUUID(),
            status = MeldingStatusType.AVVIST,
            tekst = "Noe gikk galt",
        )
        database.connection.use { connection ->
            connection.createMeldingStatus(
                meldingStatus = meldingStatus,
                meldingId = idList.first(),
            )
            connection.commit()
        }

        val result = runBlocking { ubesvartMeldingCronjob.runJob() }

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        assertNull(meldinger.first().ubesvartPublishedAt)

        verify(exactly = 0) { kafkaProducer.send(any()) }
    }
}
