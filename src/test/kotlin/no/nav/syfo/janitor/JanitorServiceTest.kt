package no.nav.syfo.janitor

import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.application.JanitorService
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventConsumerService
import no.nav.syfo.infrastructure.kafka.janitor.JANITOR_EVENT_TOPIC
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatus
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatusDTO
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatusProducer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.getMeldingFellesformat
import no.nav.syfo.testhelper.getPDFs
import no.nav.syfo.testhelper.generator.fellesformatXML
import no.nav.syfo.testhelper.generator.generateJanitorEventDTO
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class JanitorServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val meldingRepository = externalMockEnvironment.meldingRepository

    private val janitorEventStatusProducer = mockk<JanitorEventStatusProducer>()
    private val janitorService = JanitorService(
        meldingRepository = meldingRepository,
        janitorEventStatusProducer = janitorEventStatusProducer,
    )
    private val janitorEventConsumerService = JanitorEventConsumerService(janitorService = janitorService)

    @BeforeEach
    fun beforeEach() {
        clearMocks(janitorEventStatusProducer)
        justRun { janitorEventStatusProducer.sendEventStatus(any()) }
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("SLETT_BEHANDLERDIALOG")
    inner class SlettBehandlerdialog {

        @Test
        fun `Redacts melding content and sends OK status when melding exists`() = runTest {
            val melding = meldingRepository.createMeldingTilBehandler(
                meldingTilBehandler = generateMeldingTilBehandler(),
                pdf = UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER,
            )

            val event = generateJanitorEventDTO(referenceUUID = melding.uuid.toString())
            janitorService.handle(event)

            val statusSlot = slot<JanitorEventStatusDTO>()
            verify(exactly = 1) { janitorEventStatusProducer.sendEventStatus(capture(statusSlot)) }
            assertEquals(JanitorEventStatus.OK, statusSlot.captured.status)
            assertEquals(event.eventUUID, statusSlot.captured.eventUUID)

            val updatedMelding = meldingRepository.getMelding(melding.uuid)!!
            assertEquals("[Teksten er fjernet]", updatedMelding.tekst)
            assertEquals(0, updatedMelding.antallVedlegg)
            assertEquals(0, database.getPDFs(melding.uuid).size)
        }

        @Test
        fun `Redacts innkommende melding content and sends OK status`() = runTest {
            val meldingFraBehandler = generateMeldingFraBehandler()
            meldingRepository.createMeldingFraBehandler(
                meldingFraBehandler = meldingFraBehandler,
                fellesformat = fellesformatXML,
            )
            assertEquals(fellesformatXML, database.getMeldingFellesformat(meldingFraBehandler.uuid))

            val event = generateJanitorEventDTO(referenceUUID = meldingFraBehandler.uuid.toString())
            janitorService.handle(event)

            val statusSlot = slot<JanitorEventStatusDTO>()
            verify(exactly = 1) { janitorEventStatusProducer.sendEventStatus(capture(statusSlot)) }
            assertEquals(JanitorEventStatus.OK, statusSlot.captured.status)

            val updatedMelding = meldingRepository.getMelding(meldingFraBehandler.uuid)!!
            assertEquals("[Teksten er fjernet]", updatedMelding.tekst)
            assertEquals(null, database.getMeldingFellesformat(meldingFraBehandler.uuid))
        }

        @Test
        fun `Sends FAILED status when melding not found`() = runTest {
            val event = generateJanitorEventDTO(referenceUUID = UUID.randomUUID().toString())
            janitorService.handle(event)

            val statusSlot = slot<JanitorEventStatusDTO>()
            verify(exactly = 1) { janitorEventStatusProducer.sendEventStatus(capture(statusSlot)) }
            assertEquals(JanitorEventStatus.FAILED, statusSlot.captured.status)
        }

        @Test
        fun `Sends FAILED status when personident does not match melding`() = runTest {
            val melding = meldingRepository.createMeldingTilBehandler(
                meldingTilBehandler = generateMeldingTilBehandler(),
                pdf = UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER,
            )

            val event = generateJanitorEventDTO(
                referenceUUID = melding.uuid.toString(),
                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_INACTIVE.value,
            )
            janitorService.handle(event)

            val statusSlot = slot<JanitorEventStatusDTO>()
            verify(exactly = 1) { janitorEventStatusProducer.sendEventStatus(capture(statusSlot)) }
            assertEquals(JanitorEventStatus.FAILED, statusSlot.captured.status)

            val unchangedMelding = meldingRepository.getMelding(melding.uuid)!!
            assertEquals(generateMeldingTilBehandler().tekst, unchangedMelding.tekst)
        }
    }

    @Nested
    @DisplayName("Consumer integration")
    inner class ConsumerIntegration {

        @Test
        fun `Processes record from consumer and commits offset`() = runTest {
            val melding = meldingRepository.createMeldingTilBehandler(
                meldingTilBehandler = generateMeldingTilBehandler(),
                pdf = UserConstants.PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER,
            )
            val event = generateJanitorEventDTO(referenceUUID = melding.uuid.toString())
            val mockConsumer = mockKafkaConsumer(event, JANITOR_EVENT_TOPIC)

            janitorEventConsumerService.pollAndProcessRecords(mockConsumer)

            verify(exactly = 1) { mockConsumer.commitSync() }
            val updatedMelding = meldingRepository.getMelding(melding.uuid)!!
            assertEquals("[Teksten er fjernet]", updatedMelding.tekst)
        }
    }

    @Nested
    @DisplayName("Irrelevant action")
    inner class IrrelevantAction {

        @Test
        fun `Does nothing and does not produce status for unknown action`() = runTest {
            val event = generateJanitorEventDTO(action = "SOME_OTHER_ACTION")
            janitorService.handle(event)

            verify(exactly = 0) { janitorEventStatusProducer.sendEventStatus(any()) }
        }
    }
}
