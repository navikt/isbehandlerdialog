package no.nav.syfo.melding.cronjob

import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.melding.database.createMeldingTilBehandler
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.melding.kafka.producer.PublishAvvistMeldingStatusService
import no.nav.syfo.melding.status.database.createMeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatusType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateMeldingStatus
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AvvistMeldingStatusCronjobSpek : Spek({

    with(TestApplicationEngine()) {
        start()
        val database = ExternalMockEnvironment.instance.database

        val publishAvvistMeldingStatusService = PublishAvvistMeldingStatusService(
            database = database,
        )

        val avvistMeldingStatusCronjob = AvvistMeldingStatusCronjob(
            publishAvvistMeldingStatusService = publishAvvistMeldingStatusService,
            intervalDelayMinutes = ExternalMockEnvironment.instance.environment.cronjobAvvistMeldingStatusIntervalDelayMinutes
        )

        describe(AvvistMeldingStatusCronjob::class.java.simpleName) {
            describe("Test cronjob") {
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
                        val result = avvistMeldingStatusCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    val meldinger = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    meldinger.first().avvistPublishedAt shouldNotBeEqualTo null
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
                        val result = avvistMeldingStatusCronjob.runJob()

                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                }

                // TODO: Vi vet ikke hvorfor JournalforingMeldingTilBehandlerCronjobSpek ikke fungerer når denne testen er med
                // Se PR: https://github.com/navikt/isbehandlerdialog/pull/95
                // Se slack: https://nav-it.slack.com/archives/G01ABN2E885/p1689594207035479
                // Se Trello: https://nav-it.slack.com/archives/G01ABN2E885/p1689594207035479

//                it("Will not pick up other meldingstyper than AVVIST for cronjob") {
//                    val avvistMeldingStatus = generateMeldingStatus(
//                        status = MeldingStatusType.OK,
//                    )
//                    var meldingId: PMelding.Id
//
//                    database.connection.use {
//                        meldingId = it.createMeldingTilBehandler(
//                            meldingTilBehandler = generateMeldingTilBehandler(),
//                        )
//                        it.createMeldingStatus(
//                            meldingStatus = avvistMeldingStatus,
//                            meldingId = meldingId,
//                        )
//                        it.commit()
//                    }
//
//                    runBlocking {
//                        val result = avvistMeldingStatusCronjob.runJob()
//
//                        result.failed shouldBeEqualTo 0
//                        result.updated shouldBeEqualTo 0
//                    }
//
//                    val meldinger = database.getMeldingerForArbeidstaker(UserConstants.ARBEIDSTAKER_PERSONIDENT)
//                    meldinger.first().avvistPublishedAt shouldBeEqualTo null
//                }
            }
        }
    }
})
