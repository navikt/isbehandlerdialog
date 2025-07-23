package no.nav.syfo.melding.status.kafka

import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.infrastructure.kafka.DIALOGMELDING_STATUS_TOPIC
import no.nav.syfo.infrastructure.kafka.KafkaDialogmeldingStatusConsumer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateKafkaDialogmeldingStatusDTO
import no.nav.syfo.testhelper.getMeldingStatus
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class KafkaDialogmeldingStatusConsumerSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val kafkaDialogmeldingStatusConsumer = KafkaDialogmeldingStatusConsumer(
        database = database,
        meldingService = MeldingService(
            database = externalMockEnvironment.database,
            dialogmeldingBestillingProducer = mockk(),
            pdfgenClient = mockk(),
        )
    )

    afterEachTest {
        database.dropData()
    }

    describe("${KafkaDialogmeldingStatusConsumer::class.java.simpleName}: pollAndProcessRecords") {
        it("Creates no melding-status when no melding in db") {
            val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
                meldingUUID = UUID.randomUUID(),
                status = MeldingStatusType.OK,
            )
            val mockConsumer = mockKafkaConsumer(kafkaDialogmeldingStatusDTO, DIALOGMELDING_STATUS_TOPIC)

            kafkaDialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

            verify(exactly = 1) { mockConsumer.commitSync() }

            database.getMeldingStatus().size shouldBeEqualTo 0
        }
        it("Creates no melding-status for unknown melding") {
            database.connection.use {
                it.createMeldingTilBehandler(defaultMeldingTilBehandler)
            }

            val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
                meldingUUID = UUID.randomUUID(),
                status = MeldingStatusType.OK,
            )
            val mockConsumer = mockKafkaConsumer(kafkaDialogmeldingStatusDTO, DIALOGMELDING_STATUS_TOPIC)

            kafkaDialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

            verify(exactly = 1) { mockConsumer.commitSync() }

            database.getMeldingStatus().size shouldBeEqualTo 0
        }
        it("Creates new melding-status for known melding with no existing status") {
            database.connection.use {
                it.createMeldingTilBehandler(defaultMeldingTilBehandler)
            }

            val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
                meldingUUID = defaultMeldingTilBehandler.uuid,
                status = MeldingStatusType.OK,
            )
            val mockConsumer = mockKafkaConsumer(kafkaDialogmeldingStatusDTO, DIALOGMELDING_STATUS_TOPIC)

            kafkaDialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

            verify(exactly = 1) { mockConsumer.commitSync() }

            database.getMeldingStatus().size shouldBeEqualTo 1
        }
        it("Updates melding-status for known melding with existing status") {
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

            kafkaDialogmeldingStatusConsumer.pollAndProcessRecords(mockConsumer)

            verify(exactly = 1) { mockConsumer.commitSync() }

            val meldingStatus = database.getMeldingStatus()
            meldingStatus.size shouldBeEqualTo 1
            val pMeldingStatus = meldingStatus.first()
            pMeldingStatus.status shouldBeEqualTo MeldingStatusType.AVVIST.name
            pMeldingStatus.tekst shouldBeEqualTo "Avvist av EPJ"
            pMeldingStatus.updatedAt shouldBeGreaterThan pMeldingStatus.createdAt
        }
    }
})
