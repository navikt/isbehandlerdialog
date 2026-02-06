package no.nav.syfo.melding.kafka

import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.syfo.domain.MeldingTilBehandler
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.kafka.dialogmelding.DIALOGMELDING_FROM_BEHANDLER_TOPIC
import no.nav.syfo.infrastructure.kafka.dialogmelding.DialogmeldingFraBehandlerConsumer
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createMeldingerTilBehandler
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.testhelper.mock.mockKafkaConsumer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.*

class KafkaDialogmeldingFraBehandlerConsumerTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val meldingRepository = externalMockEnvironment.meldingRepository
    private val oppfolgingstilfelleClient = externalMockEnvironment.oppfolgingstilfelleClient
    private val padm2Client = externalMockEnvironment.padm2Client

    private val dialogmeldingFraBehandlerConsumer = DialogmeldingFraBehandlerConsumer(
        database = database,
        padm2Client = padm2Client,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
    )
    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT

    @AfterEach
    fun afterEach() = runTest {
        database.dropData()
    }

    @Test
    fun `Receive dialogmeldinger creates no meldinger`() = runTest {
        val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO()
        val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
    }

    @Test
    fun `Receive dialogmelding DIALOG_SVAR but unknown conversationRef creates no melding`() = runTest {
        val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO()
        val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
    }

    @Test
    fun `Receive dialogmelding DIALOG_SVAR with empty string as conversationRef doesn't fail`() = runTest {
        val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO().copy(
            conversationRef = "",
        )
        val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )
    }

    @Test
    fun `Receive dialogmelding DIALOG_SVAR with empty string as parentRef doesn't fail`() = runTest {
        val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO().copy(
            parentRef = "",
        )
        val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )
    }

    @Test
    fun `Receive dialogmelding DIALOG_NOTAT from behandler creates melding when person sykmeldt`() = runTest {
        val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatDTO()
        val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(1, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
    }

    @Test
    fun `Receive dialogmelding DIALOG_NOTAT from behandler creates no melding when person not sykmeldt`() = runTest {
        val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatDTO(
            personIdent = UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS,
        )
        val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
    }

    @Test
    fun `Receive dialogmelding DIALOG_NOTAT from behandler creates no melding when person sykmeldt long time ago`() = runTest {
        val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatDTO(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT_INACTIVE,
        )
        val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }

        assertEquals(0, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
    }

    @Nested
    @DisplayName("Having sent melding til behandler FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER")
    inner class ForesporselPasientTilleggsopplysninger {

        @Test
        fun `Receive dialogmelding DIALOG_SVAR and known conversationRef creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER`() =
            runTest {
                val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                val msgId = UUID.randomUUID()
                val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                    uuid = msgId,
                    conversationRef = conversationRef.toString(),
                )
                val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                    consumer = mockConsumer,
                )

                verify(exactly = 1) { mockConsumer.commitSync() }

                val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
                assertEquals(2, pMeldingListAfter.size)
                val pSvar = pMeldingListAfter.last()
                assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
                assertTrue(pSvar.innkommende)
                assertEquals(msgId.toString(), pSvar.msgId)
                assertEquals(UserConstants.BEHANDLER_PERSONIDENT.value, pSvar.behandlerPersonIdent)
                assertEquals(UserConstants.BEHANDLER_NAVN, pSvar.behandlerNavn)
                assertEquals(0, pSvar.antallVedlegg)
                assertNull(pSvar.veilederIdent)
                assertEquals(MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name, pSvar.type)
                val vedlegg = meldingRepository.getVedlegg(pSvar.uuid, 0)
                assertNull(vedlegg)
            }

        @Test
        @DisplayName("Receive dialogmelding DIALOG_SVAR and known conversationRef matching uuid of sent message creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER")
        fun `Receive dialogmelding DIALOG_SVAR and known conversationRef matching uuid of sent message`() = runTest {
            database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
            val msgId = UUID.randomUUID()
            val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                uuid = msgId,
                conversationRef = defaultMeldingTilBehandler.uuid.toString(),
                parentRef = UUID.randomUUID().toString(),
            )
            val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )

            verify(exactly = 1) { mockConsumer.commitSync() }

            val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
            assertEquals(2, pMeldingListAfter.size)
            val pMelding = pMeldingListAfter.first()
            val pSvar = pMeldingListAfter.last()
            assertTrue(pSvar.innkommende)
            assertEquals(msgId.toString(), pSvar.msgId)
            assertEquals(pMelding.conversationRef, pSvar.conversationRef)
        }

        @Test
        fun `Receive dialogmelding DIALOG_SVAR and parentRef matching uuid of sent message creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER`() =
            runTest {
                database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
                val msgId = UUID.randomUUID()
                val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                    uuid = msgId,
                    conversationRef = UUID.randomUUID().toString(),
                    parentRef = defaultMeldingTilBehandler.uuid.toString(),
                )
                val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                    consumer = mockConsumer,
                )

                verify(exactly = 1) { mockConsumer.commitSync() }

                val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
                assertEquals(2, pMeldingListAfter.size)
                val pMelding = pMeldingListAfter.first()
                val pSvar = pMeldingListAfter.last()
                assertTrue(pSvar.innkommende)
                assertEquals(msgId.toString(), pSvar.msgId)
                assertEquals(pMelding.conversationRef, pSvar.conversationRef)
            }

        @Test
        fun `Receive dialogmelding DIALOG_SVAR and known conversationRef and with vedlegg creates melding with vedlegg`() = runTest {
            val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
            val msgId = UserConstants.MSG_ID_WITH_VEDLEGG
            val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                uuid = msgId,
                conversationRef = conversationRef.toString(),
                antallVedlegg = 1,
            )
            val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )

            verify(exactly = 1) { mockConsumer.commitSync() }

            val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
            assertEquals(2, pMeldingListAfter.size)
            val pSvar = pMeldingListAfter.last()
            assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
            assertTrue(pSvar.innkommende)
            assertEquals(UserConstants.MSG_ID_WITH_VEDLEGG.toString(), pSvar.msgId)
            assertEquals(1, pSvar.antallVedlegg)
            assertEquals(MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name, pSvar.type)
            val vedlegg = meldingRepository.getVedlegg(pSvar.uuid, 0)
            assertArrayEquals(UserConstants.VEDLEGG_BYTEARRAY, vedlegg!!.pdf)
        }

        @Test
        fun `Receive duplicate dialogmelding DIALOG_SVAR and known conversationRef creates no duplicate melding fra behandler`() = runTest {
            val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)

            val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                conversationRef = conversationRef.toString(),
            )
            val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )
            verify(exactly = 1) { mockConsumer.commitSync() }
            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )
            verify(exactly = 2) { mockConsumer.commitSync() }

            val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
            assertEquals(2, pMeldingListAfter.size)
        }

        @Test
        fun `Receive two dialogmelding DIALOG_SVAR and known conversationRef creates two melding fra behandler`() = runTest {
            val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)

            val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                conversationRef = conversationRef.toString(),
            )
            val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )
            verify(exactly = 1) { mockConsumer.commitSync() }

            val dialogmeldingInnkommetAgain = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                conversationRef = conversationRef.toString(),
            )
            val mockConsumerAgain =
                mockKafkaConsumer(dialogmeldingInnkommetAgain, DIALOGMELDING_FROM_BEHANDLER_TOPIC)
            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumerAgain,
            )
            verify(exactly = 1) { mockConsumerAgain.commitSync() }

            val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
            assertEquals(3, pMeldingListAfter.size)
        }

        @Test
        fun `Receive DIALOG_SVAR and unknown conversationref creates no MeldingFraBehandler`() = runTest {
            database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
            val dialogmelding = generateDialogmeldingFraBehandlerForesporselSvarDTO()
            val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )

            verify(exactly = 1) { mockConsumer.commitSync() }

            assertEquals(1, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
        }

        @Test
        fun `Receive DIALOG_NOTAT and known conversationref creates MeldingFraBehandler`() = runTest {
            val (conversationRef, _) = database.createMeldingerTilBehandler(defaultMeldingTilBehandler)
            val dialogmelding =
                generateDialogmeldingFraBehandlerDialogNotatDTO(conversationRef = conversationRef.toString())
            val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )

            verify(exactly = 1) { mockConsumer.commitSync() }

            val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
            assertEquals(2, pMeldingListAfter.size)

            val pSvar = pMeldingListAfter.last()
            assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
            assertTrue(pSvar.innkommende)
            assertEquals(MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name, pSvar.type)
        }
    }

    @Nested
    @DisplayName("Having sent melding til behandler FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER and PAMINNELSE")
    inner class ForesporselPasientTilleggsopplysningerAndPaminnelse {

        @Test
        fun `Receive dialogmelding DIALOG_SVAR and known conversationRef creates melding fra behandler with type FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER`() =
            runTest {
                val meldingTilBehandler = defaultMeldingTilBehandler
                val (conversationRef, _) = database.createMeldingerTilBehandler(meldingTilBehandler)
                database.createMeldingerTilBehandler(
                    MeldingTilBehandler.createForesporselPasientPaminnelse(
                        opprinneligMelding = meldingTilBehandler,
                        veilederIdent = UserConstants.VEILEDER_IDENT,
                        document = emptyList(),
                    )
                )

                val msgId = UUID.randomUUID()
                val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
                    uuid = msgId,
                    conversationRef = conversationRef.toString(),
                )
                val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                    consumer = mockConsumer,
                )

                verify(exactly = 1) { mockConsumer.commitSync() }

                val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
                assertEquals(3, pMeldingListAfter.size)
                val pSvar = pMeldingListAfter.last()
                assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
                assertTrue(pSvar.innkommende)
                assertEquals(msgId.toString(), pSvar.msgId)
                assertEquals(MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name, pSvar.type)
            }
    }

    @Nested
    @DisplayName("Having sent melding til behandler HENVENDELSE_MELDING_FRA_NAV")
    inner class HenvendelseMeldingFraNAV {

        @Test
        fun `Receive dialogmelding DIALOG_NOTAT and known conversationRef creates MeldingFraBehandler with type HENVENDELSE_MELDING_FRA_NAV`() =
            runTest {
                val (conversationRef, _) = database.createMeldingerTilBehandler(generateMeldingTilBehandler(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV))
                val msgId = UUID.randomUUID()
                val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerDialogNotatDTO(
                    uuid = msgId,
                    conversationRef = conversationRef.toString(),
                )
                val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

                dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                    consumer = mockConsumer,
                )

                verify(exactly = 1) { mockConsumer.commitSync() }

                val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
                assertEquals(2, pMeldingListAfter.size)
                val pSvar = pMeldingListAfter.last()

                assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
                assertTrue(pSvar.innkommende)
                assertEquals(msgId.toString(), pSvar.msgId)
                assertEquals(UserConstants.BEHANDLER_PERSONIDENT.value, pSvar.behandlerPersonIdent)
                assertEquals(UserConstants.BEHANDLER_NAVN, pSvar.behandlerNavn)
                assertEquals(0, pSvar.antallVedlegg)
                assertNull(pSvar.veilederIdent)
                assertEquals(MeldingType.HENVENDELSE_MELDING_FRA_NAV.name, pSvar.type)
                assertFalse(pSvar.tekst.isNullOrEmpty())
                val vedlegg = meldingRepository.getVedlegg(pSvar.uuid, 0)
                assertNull(vedlegg)
            }

        @Test
        fun `Receive DIALOG_NOTAT and unknown conversationref creates no MeldingFraBehandler`() = runTest {
            database.createMeldingerTilBehandler(generateMeldingTilBehandler(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV))
            val dialogmelding = generateDialogmeldingFraBehandlerDialogNotatIkkeSykefravrDTO()
            val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )

            verify(exactly = 1) { mockConsumer.commitSync() }

            assertEquals(1, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
        }

        @Test
        fun `Receive DIALOG_SVAR and known conversationref creates no MeldingFraBehandler`() = runTest {
            val (conversationRef, _) = database.createMeldingerTilBehandler(generateMeldingTilBehandler(type = MeldingType.HENVENDELSE_MELDING_FRA_NAV))
            val dialogmelding =
                generateDialogmeldingFraBehandlerForesporselSvarDTO(conversationRef = conversationRef.toString())
            val mockConsumer = mockKafkaConsumer(dialogmelding, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

            dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
                consumer = mockConsumer,
            )

            verify(exactly = 1) { mockConsumer.commitSync() }

            val pMeldingListAfter = meldingRepository.getMeldingerForArbeidstaker(personIdent)
            assertEquals(2, pMeldingListAfter.size)

            val pSvar = pMeldingListAfter.last()
            assertEquals(personIdent.value, pSvar.arbeidstakerPersonIdent)
            assertTrue(pSvar.innkommende)
            assertEquals(MeldingType.HENVENDELSE_MELDING_FRA_NAV.name, pSvar.type)
        }
    }

    @Test
    fun `Receive dialogmelding DIALOG_SVAR for dialogmøte creates no melding`() = runTest {
        val dialogmeldingInnkommet = generateDialogmeldingFraBehandlerForesporselSvarDTO(
            kodeverk = "2.16.578.1.12.4.1.1.8126",
            kodeTekst = "Ja, jeg kommer",
            kode = "1",
        )
        val mockConsumer = mockKafkaConsumer(dialogmeldingInnkommet, DIALOGMELDING_FROM_BEHANDLER_TOPIC)

        dialogmeldingFraBehandlerConsumer.pollAndProcessRecords(
            consumer = mockConsumer,
        )

        verify(exactly = 1) { mockConsumer.commitSync() }
        assertEquals(0, meldingRepository.getMeldingerForArbeidstaker(personIdent).size)
    }
}
