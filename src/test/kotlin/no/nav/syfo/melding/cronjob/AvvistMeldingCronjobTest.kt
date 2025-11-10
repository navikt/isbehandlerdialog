package no.nav.syfo.melding.cronjob

import io.mockk.*
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.infrastructure.cronjob.AvvistMeldingCronjob
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import no.nav.syfo.infrastructure.kafka.producer.AvvistMeldingProducer
import no.nav.syfo.infrastructure.kafka.producer.PublishAvvistMeldingService
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateMeldingStatus
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import no.nav.syfo.testhelper.updateAvvistMeldingPublishedAt
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.Future

class AvvistMeldingCronjobTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, KafkaMeldingDTO>>()
    private val avvistMeldingProducer = AvvistMeldingProducer(kafkaProducer = kafkaProducer)

    private val publishAvvistMeldingService = PublishAvvistMeldingService(
        database = database,
        avvistMeldingProducer = avvistMeldingProducer,
    )

    private val avvistMeldingCronjob = AvvistMeldingCronjob(
        publishAvvistMeldingService = publishAvvistMeldingService,
        intervalDelayMinutes = ExternalMockEnvironment.instance.environment.cronjobAvvistMeldingStatusIntervalDelayMinutes,
    )

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
    fun `Will update avvist_published_at when cronjob is run`() {
        val avvistMeldingStatus = generateMeldingStatus(
            status = MeldingStatusType.AVVIST,
        )
        var meldingId: PMelding.Id

        database.connection.use {
            meldingId = it.createMeldingTilBehandler(
                meldingTilBehandler = generateMeldingTilBehandler(),
            )
            it.createMeldingStatus(
                meldingStatus = avvistMeldingStatus,
                meldingId = meldingId,
            )
            it.commit()
        }

        val result = avvistMeldingCronjob.runJob()

        assertEquals(0, result.failed)
        assertEquals(1, result.updated)

        val melding = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT).first()
        assertNotNull(melding.avvistPublishedAt)

        val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val kafkaMeldingDTO = producerRecordSlot.captured.value()
        assertEquals(melding.uuid.toString(), kafkaMeldingDTO.uuid)
    }

    @Test
    fun `Will not be picked up by cronjob if no unpublished avviste meldinger`() {
        val avvistMeldingStatus = generateMeldingStatus(
            status = MeldingStatusType.AVVIST,
        )
        var meldingId: PMelding.Id

        database.connection.use {
            meldingId = it.createMeldingTilBehandler(
                meldingTilBehandler = generateMeldingTilBehandler(),
            )
            it.createMeldingStatus(
                meldingStatus = avvistMeldingStatus,
                meldingId = meldingId,
            )
            it.commit()
        }
        database.updateAvvistMeldingPublishedAt(meldingId)

        val result = avvistMeldingCronjob.runJob()

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)
    }

    @Test
    fun `Will not pick up other meldingstyper than AVVIST for cronjob`() {
        val okMeldingStatus = generateMeldingStatus(
            status = MeldingStatusType.OK,
        )
        var meldingId: PMelding.Id

        database.connection.use {
            meldingId = it.createMeldingTilBehandler(
                meldingTilBehandler = generateMeldingTilBehandler(tekst = "Ikke avvist melding"),
            )
            it.createMeldingStatus(
                meldingStatus = okMeldingStatus,
                meldingId = meldingId,
            )
            it.commit()
        }

        val result = avvistMeldingCronjob.runJob()

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        val meldinger = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
        assertNull(meldinger.first().avvistPublishedAt)
    }
}
