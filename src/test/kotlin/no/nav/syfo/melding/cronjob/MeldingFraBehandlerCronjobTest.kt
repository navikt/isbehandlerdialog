package no.nav.syfo.melding.cronjob

import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.infrastructure.cronjob.MeldingFraBehandlerCronjob
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.infrastructure.database.updateInnkommendePublishedAt
import no.nav.syfo.infrastructure.kafka.producer.KafkaMeldingFraBehandlerProducer
import no.nav.syfo.infrastructure.kafka.producer.PublishMeldingFraBehandlerService
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createMeldingerFraBehandler
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.util.*

class MeldingFraBehandlerCronjobTest {

    private val database = ExternalMockEnvironment.instance.database
    private val kafkaMeldingFraBehandlerProducer = mockk<KafkaMeldingFraBehandlerProducer>()

    private val publishMeldingFraBehandlerService = PublishMeldingFraBehandlerService(
        database = database,
        kafkaMeldingFraBehandlerProducer = kafkaMeldingFraBehandlerProducer,
    )

    private val meldingFraBehandlerCronjob = MeldingFraBehandlerCronjob(
        publishMeldingFraBehandlerService = publishMeldingFraBehandlerService,
    )

    @BeforeEach
    fun beforeEach() {
        justRun {
            kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(
                kafkaMeldingDTO = any(),
                key = any(),
            )
        }
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
        clearMocks(kafkaMeldingFraBehandlerProducer)
    }

    @Test
    fun `Will update innkommende_published_at when cronjob publish on kafka`() {
        val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
        val meldingFraBehandler = generateMeldingFraBehandler(
            conversationRef = UUID.randomUUID(),
            personIdent = personIdent,
        )
        database.createMeldingerFraBehandler(
            meldingFraBehandler = meldingFraBehandler,
        )

        val result = meldingFraBehandlerCronjob.runJob()

        assertEquals(0, result.failed)
        assertEquals(1, result.updated)

        verify(exactly = 1) { kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(any(), any()) }

        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        assertNotNull(meldinger.first().innkommendePublishedAt)
    }

    @Test
    fun `Will not send to kafka if no unpublished meldingFraBehandler`() {
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

        val result = meldingFraBehandlerCronjob.runJob()

        assertEquals(0, result.failed)
        assertEquals(0, result.updated)

        verify(exactly = 0) { kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(any(), any()) }
    }
}
