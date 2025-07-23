package no.nav.syfo.melding.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.cronjob.UbesvartMeldingCronjob
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import no.nav.syfo.infrastructure.kafka.producer.KafkaUbesvartMeldingProducer
import no.nav.syfo.infrastructure.kafka.producer.PublishUbesvartMeldingService
import no.nav.syfo.infrastructure.database.createMeldingStatus
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Future

private val threeWeeksAgo = OffsetDateTime.now().minusDays(21)

class UbesvartMeldingCronjobSpek : Spek({
    val database = ExternalMockEnvironment.instance.database

    val kafkaProducer = mockk<KafkaProducer<String, KafkaMeldingDTO>>()
    val kafkaUbesvartMeldingProducer = KafkaUbesvartMeldingProducer(
        ubesvartMeldingKafkaProducer = kafkaProducer,
    )

    val publishUbesvartMeldingService = PublishUbesvartMeldingService(
        database = database,
        kafkaUbesvartMeldingProducer = kafkaUbesvartMeldingProducer,
        fristHours = ExternalMockEnvironment.instance.environment.cronjobUbesvartMeldingFristHours,
    )

    val ubesvartMeldingCronjob = UbesvartMeldingCronjob(
        publishUbesvartMeldingService = publishUbesvartMeldingService,
        intervalDelayMinutes = ExternalMockEnvironment.instance.environment.cronjobUbesvartMeldingIntervalDelayMinutes,
    )

    describe(UbesvartMeldingCronjobSpek::class.java.simpleName) {
        describe("Test cronjob") {
            val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT

            beforeEachTest {
                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }

            afterEachTest {
                database.dropData()
            }

            it("Will publish ubesvart melding til behandler foresporsel pasient when cronjob has run") {
                val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
                val (_, idList) = database.createMeldingerTilBehandler(
                    meldingTilBehandler = meldingTilBehandler,
                )
                database.updateMeldingCreatedAt(
                    id = idList.first(),
                    createdAt = threeWeeksAgo
                )

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val melding = database.getMeldingerForArbeidstaker(personIdent).first()
                melding.ubesvartPublishedAt shouldNotBeEqualTo null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaMeldingDTO = producerRecordSlot.captured.value()
                kafkaMeldingDTO.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name
                kafkaMeldingDTO.personIdent shouldBeEqualTo personIdent.value
                kafkaMeldingDTO.uuid shouldBeEqualTo melding.uuid.toString()
            }

            it("Will publish all ubesvarte meldinger when cronjob has run") {
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

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 2
                }

                val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                meldinger.first().ubesvartPublishedAt shouldNotBeEqualTo null
                meldinger.last().ubesvartPublishedAt shouldNotBeEqualTo null

                val producerRecordSlot1 = slot<ProducerRecord<String, KafkaMeldingDTO>>()
                val producerRecordSlot2 = slot<ProducerRecord<String, KafkaMeldingDTO>>()
                verifyOrder {
                    kafkaProducer.send(capture(producerRecordSlot1))
                    kafkaProducer.send(capture(producerRecordSlot2))
                }

                val firstKafkaMeldingDTO = producerRecordSlot1.captured.value()
                val secondKafkaMeldingDTO = producerRecordSlot2.captured.value()
                firstKafkaMeldingDTO.uuid shouldBeEqualTo meldinger.first().uuid.toString()
                secondKafkaMeldingDTO.uuid shouldBeEqualTo meldinger.last().uuid.toString()
            }

            it("Will not publish any ubesvart melding when no melding older than 3 weeks") {
                val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
                val (_, idList) = database.createMeldingerTilBehandler(
                    meldingTilBehandler = meldingTilBehandler,
                )
                database.updateMeldingCreatedAt(
                    id = idList.first(),
                    createdAt = OffsetDateTime.now().minusDays(20)
                )

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                meldinger.first().ubesvartPublishedAt shouldBeEqualTo null

                verify(exactly = 0) { kafkaProducer.send(any()) }
            }

            it("Will not publish ubesvart melding when melding is besvart") {
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

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                meldinger.first().ubesvartPublishedAt shouldBeEqualTo null

                verify(exactly = 0) { kafkaProducer.send(any()) }
            }

            it("Will publish ubesvart melding when newest melding in conversation is ubesvart") {
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

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                val utgaendeMeldinger = meldinger.filter { !it.innkommende }
                utgaendeMeldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                utgaendeMeldinger.last().ubesvartPublishedAt shouldNotBeEqualTo null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaMeldingDTO = producerRecordSlot.captured.value()
                kafkaMeldingDTO.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name
                kafkaMeldingDTO.personIdent shouldBeEqualTo personIdent.value
                kafkaMeldingDTO.uuid shouldBeEqualTo utgaendeMeldinger.last().uuid.toString()
            }

            it("Will publish ubesvart melding when melding is of type legeeklaring and cronjob has run") {
                val meldingTilBehandler =
                    generateMeldingTilBehandler(personIdent = personIdent, type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)
                val (_, idList) = database.createMeldingerTilBehandler(
                    meldingTilBehandler = meldingTilBehandler,
                )
                database.updateMeldingCreatedAt(
                    id = idList.first(),
                    createdAt = threeWeeksAgo
                )

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val melding = database.getMeldingerForArbeidstaker(personIdent).first()
                melding.ubesvartPublishedAt shouldNotBeEqualTo null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaMeldingDTO>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaMeldingDTO = producerRecordSlot.captured.value()
                kafkaMeldingDTO.type shouldBeEqualTo MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING.name
                kafkaMeldingDTO.personIdent shouldBeEqualTo personIdent.value
                kafkaMeldingDTO.uuid shouldBeEqualTo melding.uuid.toString()
            }

            it("Will not publish ubesvart melding when melding is of type paminnelse") {
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

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                meldinger.first().ubesvartPublishedAt shouldBeEqualTo null

                verify(exactly = 0) { kafkaProducer.send(any()) }
            }

            it("Will not publish ubesvart melding when melding is of type henvendelse melding fra NAV") {
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

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                meldinger.first().ubesvartPublishedAt shouldBeEqualTo null

                verify(exactly = 0) { kafkaProducer.send(any()) }
            }

            it("Will not publish ubesvart melding when melding has avvist apprec status") {
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

                runBlocking {
                    val result = ubesvartMeldingCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                meldinger.first().ubesvartPublishedAt shouldBeEqualTo null

                verify(exactly = 0) { kafkaProducer.send(any()) }
            }
        }
    }
})
