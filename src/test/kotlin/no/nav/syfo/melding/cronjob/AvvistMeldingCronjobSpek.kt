package no.nav.syfo.melding.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.cronjob.AvvistMeldingCronjob
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import no.nav.syfo.infrastructure.kafka.producer.AvvistMeldingProducer
import no.nav.syfo.infrastructure.kafka.producer.PublishAvvistMeldingService
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateMeldingStatus
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import no.nav.syfo.testhelper.updateAvvistMeldingPublishedAt
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

class AvvistMeldingCronjobSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val kafkaProducer = mockk<KafkaProducer<String, KafkaMeldingDTO>>()
    val avvistMeldingProducer = AvvistMeldingProducer(kafkaProducer = kafkaProducer)

    val publishAvvistMeldingService = PublishAvvistMeldingService(
        database = database,
        avvistMeldingProducer = avvistMeldingProducer,
    )

    val avvistMeldingCronjob = AvvistMeldingCronjob(
        publishAvvistMeldingService = publishAvvistMeldingService,
        intervalDelayMinutes = ExternalMockEnvironment.instance.environment.cronjobAvvistMeldingStatusIntervalDelayMinutes,
    )

    describe(AvvistMeldingCronjob::class.java.simpleName) {
        describe("Test cronjob") {
            beforeEachTest {
                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }
            afterEachTest {
                database.dropData()
            }

            it("Will update avvist_published_at when cronjob is run") {
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

                runBlocking {
                    val result = avvistMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val melding = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT).first()
                melding.avvistPublishedAt shouldNotBeEqualTo null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaMeldingDTO = producerRecordSlot.captured.value()
                kafkaMeldingDTO.uuid shouldBeEqualTo melding.uuid.toString()
            }

            it("Will not be picked up by cronjob if no unpublished avviste meldinger") {
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

                runBlocking {
                    val result = avvistMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }
            }
            it("Will not pick up other meldingstyper than AVVIST for cronjob") {
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

                runBlocking {
                    val result = avvistMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                val meldinger = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                meldinger.first().avvistPublishedAt shouldBeEqualTo null
            }
        }
    }
})
