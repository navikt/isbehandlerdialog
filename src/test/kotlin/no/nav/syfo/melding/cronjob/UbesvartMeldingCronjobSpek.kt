package no.nav.syfo.melding.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.KafkaUbesvartMeldingProducer
import no.nav.syfo.melding.kafka.PublishUbesvartMeldingService
import no.nav.syfo.melding.status.database.createMeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatusType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

class UbesvartMeldingCronjobSpek : Spek({

    with(TestApplicationEngine()) {
        start()
        val database = ExternalMockEnvironment.instance.database

        val kafkaUbesvartMeldingProducer = mockk<KafkaUbesvartMeldingProducer>()

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
                    justRun {
                        kafkaUbesvartMeldingProducer.sendUbesvartMelding(
                            key = any(),
                            meldingTilBehandler = any(),
                        )
                    }
                }

                afterEachTest {
                    database.dropData()
                    clearMocks(kafkaUbesvartMeldingProducer)
                }

                it("Will update ubesvart_published_at when cronjob has run") {
                    val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
                    val (_, idList) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandler,
                    )
                    database.updateMeldingCreatedAt(
                        id = idList.first(),
                        createdAt = OffsetDateTime.now().minusDays(14)
                    )

                    runBlocking {
                        val result = ubesvartMeldingCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    verify(exactly = 1) { kafkaUbesvartMeldingProducer.sendUbesvartMelding(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldNotBeEqualTo null
                }

                it("Will update ubesvart_published_at for several ubesvarte when cronjob has run") {
                    val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
                    val (_, idList) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandler,
                    )
                    database.updateMeldingCreatedAt(
                        id = idList.first(),
                        createdAt = OffsetDateTime.now().minusDays(14)
                    )

                    val meldingTilBehandlerWithOtherConversationRef = generateMeldingTilBehandler(personIdent)
                    val (_, idListForMeldingWithOtherConversationRef) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandlerWithOtherConversationRef,
                    )
                    database.updateMeldingCreatedAt(
                        id = idListForMeldingWithOtherConversationRef.first(),
                        createdAt = OffsetDateTime.now().minusDays(14)
                    )

                    runBlocking {
                        val result = ubesvartMeldingCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 2
                    }

                    verify(exactly = 2) { kafkaUbesvartMeldingProducer.sendUbesvartMelding(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldNotBeEqualTo null
                    meldinger.last().ubesvartPublishedAt shouldNotBeEqualTo null
                }

                it("Will not update ubesvart_published_at when no melding older than 2 weeks") {
                    val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
                    val (_, idList) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandler,
                    )
                    database.updateMeldingCreatedAt(
                        id = idList.first(),
                        createdAt = OffsetDateTime.now().minusDays(13)
                    )

                    runBlocking {
                        val result = ubesvartMeldingCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }

                    verify(exactly = 0) { kafkaUbesvartMeldingProducer.sendUbesvartMelding(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                }

                it("Will not update ubesvart_published_at when melding is besvart") {
                    val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
                    val (conversationRef, idList) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandler,
                    )
                    database.updateMeldingCreatedAt(
                        id = idList.first(),
                        createdAt = OffsetDateTime.now().minusDays(14)
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

                    verify(exactly = 0) { kafkaUbesvartMeldingProducer.sendUbesvartMelding(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                }

                it("Will update ubesvart_published_at when newest melding in conversation is ubesvart") {
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
                        createdAt = OffsetDateTime.now().minusDays(14)
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
                        createdAt = OffsetDateTime.now().minusDays(20)
                    )

                    runBlocking {
                        val result = ubesvartMeldingCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    verify(exactly = 1) { kafkaUbesvartMeldingProducer.sendUbesvartMelding(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    val utgaendeMeldinger = meldinger.filter { !it.innkommende }
                    utgaendeMeldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                    utgaendeMeldinger.last().ubesvartPublishedAt shouldNotBeEqualTo null
                }

                it("Will not update ubesvart_published_at when melding is of type paminnelse") {
                    val meldingTilBehandler = generateMeldingTilBehandler(
                        personIdent = personIdent,
                        type = MeldingType.FORESPORSEL_PASIENT_PAMINNELSE,
                    )
                    val (_, idList) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandler,
                    )
                    database.updateMeldingCreatedAt(
                        id = idList.first(),
                        createdAt = OffsetDateTime.now().minusDays(14)
                    )

                    runBlocking {
                        val result = ubesvartMeldingCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }

                    verify(exactly = 0) { kafkaUbesvartMeldingProducer.sendUbesvartMelding(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                }

                it("Will not update ubesvart_published_at when melding has avvist apprec status") {
                    val meldingTilBehandler = generateMeldingTilBehandler(personIdent)
                    val (_, idList) = database.createMeldingerTilBehandler(
                        meldingTilBehandler = meldingTilBehandler,
                    )
                    database.updateMeldingCreatedAt(
                        id = idList.first(),
                        createdAt = OffsetDateTime.now().minusDays(14)
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

                    verify(exactly = 0) { kafkaUbesvartMeldingProducer.sendUbesvartMelding(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                }
            }
        }
    }
})
