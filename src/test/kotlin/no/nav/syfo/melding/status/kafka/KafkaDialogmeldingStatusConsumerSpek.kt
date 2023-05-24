package no.nav.syfo.melding.status.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.melding.api.toMeldingTilBehandler
import no.nav.syfo.melding.database.createMeldingTilBehandler
import no.nav.syfo.melding.status.database.createMeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatusType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateKafkaDialogmeldingStatusDTO
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandlerRequestDTO
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

val meldingTilBehandler = generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(
    personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
)

class KafkaDialogmeldingStatusConsumerSpek : Spek({
    with(TestApplicationEngine()) {
        start()
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
                    it.createMeldingTilBehandler(meldingTilBehandler)
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
                    it.createMeldingTilBehandler(meldingTilBehandler)
                }

                val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
                    meldingUUID = meldingTilBehandler.uuid,
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
                        it.createMeldingTilBehandler(meldingTilBehandler = meldingTilBehandler, commit = false)
                    it.createMeldingStatus(
                        meldingStatus = existingStatus,
                        meldingId = meldingId,
                    )
                    it.commit()
                }
                val kafkaDialogmeldingStatusDTO = generateKafkaDialogmeldingStatusDTO(
                    meldingUUID = meldingTilBehandler.uuid,
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
    }
})
