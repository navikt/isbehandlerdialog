package no.nav.syfo.melding.status.kafka

import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.kafka.DIALOGMELDING_STATUS_TOPIC
import no.nav.syfo.infrastructure.kafka.DialogmeldingStatusConsumer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateKafkaDialogmeldingStatusDTO
import no.nav.syfo.testhelper.getMeldingStatus
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class KafkaDialogmeldingStatusConsumerTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val dialogmeldingStatusConsumer = DialogmeldingStatusConsumer(
        database = database,
        meldingRepository = externalMockEnvironment.meldingRepository,
        meldingService = externalMockEnvironment.meldingService,
    )

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `Creates no melding-status when no melding in db`() = runTest {
        val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
            meldingUUID = UUID.randomUUID(),
            status = MeldingStatusType.OK,
        )
        val mockConsumer = mockKafkaConsumer(kafkaDialogmeldingStatusDTO, DIALOGMELDING_STATUS_TOPIC)

        dialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, database.getMeldingStatus().size)
    }

    @Test
    fun `Creates no melding-status for unknown melding`() = runTest {
        database.connection.use {
            it.createMeldingTilBehandler(defaultMeldingTilBehandler)
        }

        val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
            meldingUUID = UUID.randomUUID(),
            status = MeldingStatusType.OK,
        )
        val mockConsumer = mockKafkaConsumer(kafkaDialogmeldingStatusDTO, DIALOGMELDING_STATUS_TOPIC)

        dialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, database.getMeldingStatus().size)
    }

    @Test
    fun `Creates new melding-status for known melding with no existing status`() = runTest {
        database.connection.use {
            it.createMeldingTilBehandler(defaultMeldingTilBehandler)
        }

        val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
            meldingUUID = defaultMeldingTilBehandler.uuid,
            status = MeldingStatusType.OK,
        )
        val mockConsumer = mockKafkaConsumer(kafkaDialogmeldingStatusDTO, DIALOGMELDING_STATUS_TOPIC)

        dialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(1, database.getMeldingStatus().size)
    }

    @Test
    fun `Updates melding-status for known melding with existing status`() = runTest {
        val existingStatus = MeldingStatus(
            uuid = UUID.randomUUID(),
            status = MeldingStatusType.SENDT,
            tekst = null
        )
        database.connection.use {
            val meldingId =
                it.createMeldingTilBehandler(meldingTilBehandler = defaultMeldingTilBehandler, commit = false)
            it.createMeldingStatus(
                meldingStatus = existingStatus,
                meldingId = meldingId,
            )
            it.commit()
        }
        val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
            meldingUUID = defaultMeldingTilBehandler.uuid,
            status = MeldingStatusType.AVVIST,
            tekst = "Avvist av EPJ"
        )
        val mockConsumer = mockKafkaConsumer(kafkaDialogmeldingStatusDTO, DIALOGMELDING_STATUS_TOPIC)

        dialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

        verify(exactly = 1) { mockConsumer.commitSync() }

        val meldingStatus = database.getMeldingStatus()
        assertEquals(1, meldingStatus.size)
        val pMeldingStatus = meldingStatus.first()
        assertEquals(MeldingStatusType.AVVIST.name, pMeldingStatus.status)
        assertEquals("Avvist av EPJ", pMeldingStatus.tekst)
        assertTrue(pMeldingStatus.updatedAt > pMeldingStatus.createdAt)
    }
}
