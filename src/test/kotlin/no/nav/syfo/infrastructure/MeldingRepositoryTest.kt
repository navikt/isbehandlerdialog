package no.nav.syfo.infrastructure

import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.database.repository.MeldingRepository
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createMeldingerTilBehandler
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.util.UUID

class MeldingRepositoryTest {

    private val database = ExternalMockEnvironment.instance.database
    private val meldingRepository = MeldingRepository(database)

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    @Nested
    @DisplayName("getMelding")
    inner class GetMeldingTest {

        @Test
        @DisplayName("Returns melding when it exists")
        fun `returns melding when it exists`() {
            val meldingTilBehandler = generateMeldingTilBehandler(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            )
            database.createMeldingerTilBehandler(
                meldingTilBehandler = meldingTilBehandler,
                numberOfMeldinger = 1,
            )

            val result = runBlocking { meldingRepository.getMelding(meldingTilBehandler.uuid) }

            assertNotNull(result)
            assertEquals(meldingTilBehandler.uuid, result.uuid)
            assertEquals(meldingTilBehandler.arbeidstakerPersonIdent.value, result.arbeidstakerPersonIdent)
            assertEquals(meldingTilBehandler.tekst, result.tekst)
            assertEquals(meldingTilBehandler.type.name, result.type)
            assertEquals(meldingTilBehandler.conversationRef, result.conversationRef)
        }

        @Test
        @DisplayName("Returns melding with correct document content")
        fun `returns melding with correct document content`() {
            val meldingTilBehandler = defaultMeldingTilBehandler.copy(
                uuid = UUID.randomUUID(),
            )
            database.createMeldingerTilBehandler(
                meldingTilBehandler = meldingTilBehandler,
                numberOfMeldinger = 1,
            )

            val result = runBlocking { meldingRepository.getMelding(meldingTilBehandler.uuid) }

            assertNotNull(result)
            assertNotNull(result.document)
            assertEquals(meldingTilBehandler.document.size, result.document.size)
            assertEquals(meldingTilBehandler.antallVedlegg, result.antallVedlegg)
        }

        @Test
        @DisplayName("Returns null when melding does not exist")
        fun `returns null when melding does not exist`() {
            val nonExistentUuid = UUID.randomUUID()
            val result = runBlocking { meldingRepository.getMelding(nonExistentUuid) }

            assertNull(result)
        }

        @Test
        @DisplayName("Returns correct melding when multiple meldinger exist")
        fun `returns correct melding when multiple meldinger exist`() {
            val firstMelding = generateMeldingTilBehandler(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            )
            val secondMelding = generateMeldingTilBehandler(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            )

            database.createMeldingerTilBehandler(firstMelding, 1)
            database.createMeldingerTilBehandler(secondMelding, 1)

            val result = runBlocking { meldingRepository.getMelding(secondMelding.uuid) }

            assertNotNull(result)
            assertEquals(secondMelding.uuid, result.uuid)
            assertEquals(secondMelding.conversationRef, result.conversationRef)
            assertEquals(secondMelding.tekst, result.tekst)
        }
    }
}
