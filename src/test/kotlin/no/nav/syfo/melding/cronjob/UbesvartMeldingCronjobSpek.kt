package no.nav.syfo.melding.cronjob

import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.melding.kafka.PublishUbesvartMeldingService
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime

class UbesvartMeldingCronjobSpek : Spek({

    with(TestApplicationEngine()) {
        start()
        val database = ExternalMockEnvironment.instance.database

        val publishUbesvartMeldingService = PublishUbesvartMeldingService(
            database = database,
            fristHours = ExternalMockEnvironment.instance.environment.cronjobUbesvartMeldingFristHours,
        )

        val ubesvartMeldingCronjob = UbesvartMeldingCronjob(
            publishUbesvartMeldingService = publishUbesvartMeldingService,
            intervalDelayMinutes = ExternalMockEnvironment.instance.environment.cronjobUbesvartMeldingIntervalDelayMinutes,
        )

        describe(UbesvartMeldingCronjobSpek::class.java.simpleName) {
            describe("Test cronjob") {
                afterEachTest {
                    database.dropData()
                }

                it("Will update ubesvart_published_at when cronjob has run") {
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
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

                    // TODO: Add check for publish to kafka

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldNotBeEqualTo null
                }

                it("Will update ubesvart_published_at for several ubesvarte when cronjob has run") {
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
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

                    // TODO: Add check for publish to kafka

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldNotBeEqualTo null
                    meldinger.last().ubesvartPublishedAt shouldNotBeEqualTo null
                }

                it("Will not update ubesvart_published_at when no melding older than 2 weeks") {
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
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

                    // TODO: Add check for no publish to kafka

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                }

                it("Will not update ubesvart_published_at when melding is besvart") {
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
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

                    // TODO: Add check for no publish to kafka

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                }

                it("Will update ubesvart_published_at when newest melding in conversation is ubesvart") {
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
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

                    // TODO: Add check for publish to kafka

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    val utgaendeMeldinger = meldinger.filter { !it.innkommende }
                    utgaendeMeldinger.first().ubesvartPublishedAt shouldBeEqualTo null
                    utgaendeMeldinger.last().ubesvartPublishedAt shouldNotBeEqualTo null
                }
            }
        }
    }
})
