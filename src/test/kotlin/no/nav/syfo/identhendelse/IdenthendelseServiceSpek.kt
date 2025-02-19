package no.nav.syfo.identhendelse

import no.nav.syfo.melding.database.createMeldingFraBehandler
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateKafkaIdenthendelseDTO
import no.nav.syfo.testhelper.generator.generateMeldingFraBehandler
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IdenthendelseServiceSpek : Spek({

    describe(IdenthendelseServiceSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val identhendelseService = IdenthendelseService(
            database = database,
        )

        afterEachTest {
            database.dropData()
        }

        describe("Happy path") {
            it("Skal oppdatere database når person har fått ny ident") {
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
                meldingerFraBehandlerForOldIdent.size shouldBeEqualTo 0
                val meldingerFraBehandler = database.getMeldingerForArbeidstaker(newIdent)
                meldingerFraBehandler.size shouldBeEqualTo 1
            }
            it("Skal ikke oppdatere database når melding allerede har ny iden") {
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
                meldingerFraBehandlerForOldIdent.size shouldBeEqualTo 0
                val meldingerFraBehandler = database.getMeldingerForArbeidstaker(newIdent)
                meldingerFraBehandler.size shouldBeEqualTo 1
            }
        }
    }
})
