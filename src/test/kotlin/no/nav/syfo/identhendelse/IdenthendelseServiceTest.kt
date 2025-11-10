package no.nav.syfo.identhendelse

import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.infrastructure.database.createMeldingFraBehandler
import no.nav.syfo.infrastructure.database.getMeldingerForArbeidstaker
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateKafkaIdenthendelseDTO
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdenthendelseServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val identhendelseService = IdenthendelseService(
        database = database,
    )

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `Skal oppdatere database når person har fått ny ident`() {
        val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO()
        val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
        val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

        val melding = generateMeldingFraBehandler(personIdent = oldIdent)
        database.connection.use { connection ->
            connection.createMeldingFraBehandler(
                meldingFraBehandler = melding,
                commit = true,
            )
        }

        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

        val meldingerFraBehandlerForOldIdent = database.getMeldingerForArbeidstaker(oldIdent)
        assertEquals(0, meldingerFraBehandlerForOldIdent.size)
        val meldingerFraBehandler = database.getMeldingerForArbeidstaker(newIdent)
        assertEquals(1, meldingerFraBehandler.size)
    }

    @Test
    fun `Skal ikke oppdatere database når melding allerede har ny ident`() {
        val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO()
        val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
        val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

        val melding = generateMeldingFraBehandler(personIdent = newIdent)
        database.connection.use { connection ->
            connection.createMeldingFraBehandler(
                meldingFraBehandler = melding,
                commit = true,
            )
        }

        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

        val meldingerFraBehandlerForOldIdent = database.getMeldingerForArbeidstaker(oldIdent)
        assertEquals(0, meldingerFraBehandlerForOldIdent.size)
        val meldingerFraBehandler = database.getMeldingerForArbeidstaker(newIdent)
        assertEquals(1, meldingerFraBehandler.size)
    }
}
