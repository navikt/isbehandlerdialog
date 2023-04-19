package no.nav.syfo.melding.cronjob

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.melding.database.updateInnkommendePublishedAt
import no.nav.syfo.melding.kafka.KafkaMeldingFraBehandlerProducer
import no.nav.syfo.melding.kafka.PublishMeldingFraBehandlerService
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createMeldingerFraBehandler
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class MeldingFraBehandlerCronjobSpek : Spek({

    with(TestApplicationEngine()) {
        start()
        val database = ExternalMockEnvironment.instance.database

        val kafkaMeldingFraBehandlerProducer = mockk<KafkaMeldingFraBehandlerProducer>()
        justRun {
            kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(
                kafkaMeldingFraBehandlerDTO = any(),
                key = any(),
            )
        }

        val publishMeldingFraBehandlerService = PublishMeldingFraBehandlerService(
            database = database,
            kafkaMeldingFraBehandlerProducer = kafkaMeldingFraBehandlerProducer,
        )

        val meldingFraBehandlerCronjob = MeldingFraBehandlerCronjob(
            publishMeldingFraBehandlerService = publishMeldingFraBehandlerService,
        )

        describe(MeldingFraBehandlerCronjob::class.java.simpleName) {
            describe("Test cronjob") {
                afterEachTest {
                    database.dropData()
                    clearMocks(kafkaMeldingFraBehandlerProducer)
                }

                it("Will update innkommende_published_at when cronjob publish on kafka") {
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    val meldingFraBehandler = generateMeldingFraBehandler(
                        conversationRef = UUID.randomUUID(),
                        personIdent = personIdent,
                    )
                    database.createMeldingerFraBehandler(
                        meldingFraBehandler = meldingFraBehandler,
                    )

                    runBlocking {
                        val result = meldingFraBehandlerCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    verify(exactly = 1) { kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(any(), any()) }

                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    meldinger.first().innkommendePublishedAt shouldNotBeEqualTo null
                }

                it("Will not send to kafka if no unpublished meldingFraBehandler") {
                    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    val meldingFraBehandler = generateMeldingFraBehandler(
                        conversationRef = UUID.randomUUID(),
                        personIdent = personIdent,
                    )
                    database.createMeldingerFraBehandler(
                        meldingFraBehandler = meldingFraBehandler,
                    )
                    val meldinger = database.getMeldingerForArbeidstaker(personIdent)
                    database.updateInnkommendePublishedAt(uuid = meldinger.first().uuid)

                    runBlocking {
                        val result = meldingFraBehandlerCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }

                    verify(exactly = 0) { kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(any(), any()) }
                }
            }
        }
    }
})
