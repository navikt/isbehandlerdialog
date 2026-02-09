package no.nav.syfo.domain

import no.nav.syfo.infrastructure.client.dokarkiv.domain.AvsenderMottaker
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Bruker
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrukerIdType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Dokument
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Dokumentvariant
import no.nav.syfo.infrastructure.client.dokarkiv.domain.FiltypeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.infrastructure.client.dokarkiv.domain.MeldingTittel
import no.nav.syfo.infrastructure.client.dokarkiv.domain.OverstyrInnsynsregler
import no.nav.syfo.infrastructure.client.dokarkiv.domain.VariantformatType
import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingKode
import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingKodeverk
import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingType
import java.time.OffsetDateTime
import java.util.*

const val MOTTATT_LEGEERKLARING_TEKST = "Mottatt legeerklæring"

sealed class Melding(
    open val uuid: UUID,
    open val createdAt: OffsetDateTime,
    open val msgId: String?,
    open val type: MeldingType,
    open val conversationRef: UUID,
    open val innkommende: Boolean,
    open val tidspunkt: OffsetDateTime,
    open val parentRef: UUID?,
    open val tekst: String?,
    open val arbeidstakerPersonIdent: PersonIdent,
    open val behandlerPersonIdent: PersonIdent?,
    open val behandlerNavn: String?,
    open val behandlerRef: UUID?,
    open val antallVedlegg: Int,
    open val document: List<DocumentComponentDTO>,
    open val journalpostId: String?,
    open val veilederIdent: String?,
) {
    data class MeldingTilBehandler(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val msgId: String? = null,
        override val type: MeldingType,
        override val conversationRef: UUID,
        override val innkommende: Boolean = false,
        override val tidspunkt: OffsetDateTime,
        override val parentRef: UUID?,
        override val tekst: String?,
        override val arbeidstakerPersonIdent: PersonIdent,
        override val behandlerPersonIdent: PersonIdent?,
        override val behandlerNavn: String?,
        override val behandlerRef: UUID,
        override val antallVedlegg: Int,
        override val document: List<DocumentComponentDTO>,
        override val journalpostId: String? = null,
        override val veilederIdent: String?,
        val ubesvartPublishedAt: OffsetDateTime?,
    ) : Melding(
        uuid,
        createdAt,
        msgId,
        type,
        conversationRef,
        innkommende,
        tidspunkt,
        parentRef,
        tekst,
        arbeidstakerPersonIdent,
        behandlerPersonIdent,
        behandlerNavn,
        behandlerRef,
        antallVedlegg,
        document,
        journalpostId,
        veilederIdent
    ) {
        fun toJournalpostRequest(behandlerHprId: Int?, pdf: ByteArray) =
            JournalpostRequest(
                avsenderMottaker = createAvsenderMottaker(
                    behandlerPersonIdent = behandlerPersonIdent,
                    behandlerHprId = behandlerHprId,
                    behandlerNavn = behandlerNavn
                ),
                tittel = this.createTittel(),
                bruker = Bruker.create(
                    id = arbeidstakerPersonIdent.value,
                    idType = BrukerIdType.PERSON_IDENT,
                ),
                dokumenter = listOf(
                    Dokument.create(
                        brevkode = this.getBrevKode(),
                        tittel = this.createTittel(),
                        dokumentvarianter = listOf(
                            Dokumentvariant.create(
                                filnavn = this.createTittel(),
                                filtype = FiltypeType.PDFA,
                                fysiskDokument = pdf,
                                variantformat = VariantformatType.ARKIV,
                            )
                        ),
                    )
                ),
                overstyrInnsynsregler = this.createOverstyrInnsynsregler(),
                eksternReferanseId = uuid.toString(),
            )

        private fun createAvsenderMottaker(
            behandlerPersonIdent: PersonIdent?,
            behandlerHprId: Int?,
            behandlerNavn: String?,
        ): AvsenderMottaker {
            return if (behandlerHprId != null) {
                AvsenderMottaker.create(
                    id = hprNrWithNineDigits(behandlerHprId),
                    idType = BrukerIdType.HPRNR,
                    navn = behandlerNavn,
                )
            } else if (behandlerPersonIdent == null) {
                AvsenderMottaker.create(
                    id = null,
                    idType = null,
                    navn = behandlerNavn,
                )
            } else {
                AvsenderMottaker.create(
                    id = behandlerPersonIdent.value,
                    idType = BrukerIdType.PERSON_IDENT,
                    navn = behandlerNavn,
                )
            }
        }

        fun createTittel(): String =
            when (this.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> MeldingTittel.DIALOGMELDING_DEFAULT.value
                MeldingType.HENVENDELSE_MELDING_TIL_NAV -> MeldingTittel.DIALOGMELDING_TIL_NAV.value
                MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> MeldingTittel.DIALOGMELDING_PAMINNELSE.value
                MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> MeldingTittel.DIALOGMELDING_RETUR.value
            }

        fun createOverstyrInnsynsregler(): String? =
            when (this.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.value
                MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> null
            }

        fun getDialogmeldingKode(): DialogmeldingKode =
            when (this.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingKode.FORESPORSEL
                MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> DialogmeldingKode.PAMINNELSE_FORESPORSEL
                MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> DialogmeldingKode.RETUR_LEGEERKLARING
                MeldingType.HENVENDELSE_MELDING_FRA_NAV -> DialogmeldingKode.MELDING_FRA_NAV
                MeldingType.HENVENDELSE_MELDING_TIL_NAV -> DialogmeldingKode.HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING
            }

        fun getDialogmeldingKodeverk(): DialogmeldingKodeverk =
            when (this.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingKodeverk.FORESPORSEL
                MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> DialogmeldingKodeverk.HENVENDELSE
            }

        fun getDialogmeldingType(): DialogmeldingType =
            when (this.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingType.DIALOG_FORESPORSEL
                MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> DialogmeldingType.DIALOG_NOTAT
            }

        private fun getBrevKode(): BrevkodeType =
            when (this.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> BrevkodeType.FORESPORSEL_OM_PASIENT
                MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> BrevkodeType.FORESPORSEL_OM_PASIENT_PAMINNELSE
                MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> BrevkodeType.HENVENDELSE_RETUR_LEGEERKLARING
                MeldingType.HENVENDELSE_MELDING_FRA_NAV -> BrevkodeType.HENVENDELSE_MELDING_FRA_NAV
                MeldingType.HENVENDELSE_MELDING_TIL_NAV -> BrevkodeType.HENVENDELSE_MELDING_TIL_NAV
            }

        private fun kanHaPaminnelse(): Boolean =
            when (this.type) {
                MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> true
                MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.HENVENDELSE_MELDING_FRA_NAV, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> false
            }

        enum class MeldingType2 {
            FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER
        }

        companion object {
            fun createMeldingTilBehandler(
                type: MeldingType,
                veilederIdent: String,
                document: List<DocumentComponentDTO>,
                personIdent: PersonIdent,
                behandlerIdent: String?,
                behandlerNavn: String?,
                behandlerRef: UUID,
                tekst: String,
            ): MeldingTilBehandler = create(
                type = type,
                conversationRef = UUID.randomUUID(),
                personIdent = personIdent,
                behandlerPersonIdent = behandlerIdent?.let { PersonIdent(behandlerIdent) },
                behandlerNavn = behandlerNavn,
                behandlerRef = behandlerRef,
                tekst = tekst,
                document = document,
                veilederIdent = veilederIdent
            )

            fun createForesporselPasientPaminnelse(
                opprinneligMelding: MeldingTilBehandler,
                veilederIdent: String,
                document: List<DocumentComponentDTO>,
            ): MeldingTilBehandler {
                if (!opprinneligMelding.kanHaPaminnelse()) {
                    throw IllegalArgumentException("Kan ikke opprette påminnelse for melding av type ${opprinneligMelding.type}")
                }

                return create(
                    type = MeldingType.FORESPORSEL_PASIENT_PAMINNELSE,
                    conversationRef = opprinneligMelding.conversationRef,
                    parentRef = opprinneligMelding.uuid,
                    personIdent = opprinneligMelding.arbeidstakerPersonIdent,
                    behandlerPersonIdent = opprinneligMelding.behandlerPersonIdent,
                    behandlerNavn = opprinneligMelding.behandlerNavn,
                    behandlerRef = opprinneligMelding.behandlerRef,
                    tekst = "",
                    document = document,
                    veilederIdent = veilederIdent,
                )
            }

            fun createReturAvLegeerklaring(
                opprinneligForesporselLegeerklaring: MeldingTilBehandler,
                innkommendeLegeerklaring: MeldingFraBehandler,
                veilederIdent: String,
                document: List<DocumentComponentDTO>,
                tekst: String,
            ): MeldingTilBehandler = create(
                type = MeldingType.HENVENDELSE_RETUR_LEGEERKLARING,
                conversationRef = opprinneligForesporselLegeerklaring.conversationRef,
                parentRef = innkommendeLegeerklaring.uuid,
                personIdent = opprinneligForesporselLegeerklaring.arbeidstakerPersonIdent,
                behandlerPersonIdent = opprinneligForesporselLegeerklaring.behandlerPersonIdent,
                behandlerNavn = opprinneligForesporselLegeerklaring.behandlerNavn,
                behandlerRef = opprinneligForesporselLegeerklaring.behandlerRef,
                tekst = tekst,
                document = document,
                veilederIdent = veilederIdent,
            )

            private fun create(
                type: MeldingType,
                conversationRef: UUID,
                parentRef: UUID? = null,
                personIdent: PersonIdent,
                behandlerPersonIdent: PersonIdent?,
                behandlerNavn: String?,
                behandlerRef: UUID,
                tekst: String,
                document: List<DocumentComponentDTO>,
                veilederIdent: String,
            ): MeldingTilBehandler {
                val now = OffsetDateTime.now()
                return MeldingTilBehandler(
                    uuid = UUID.randomUUID(),
                    createdAt = now,
                    type = type,
                    conversationRef = conversationRef,
                    parentRef = parentRef,
                    tidspunkt = now,
                    arbeidstakerPersonIdent = personIdent,
                    behandlerPersonIdent = behandlerPersonIdent,
                    behandlerNavn = behandlerNavn,
                    behandlerRef = behandlerRef,
                    tekst = tekst,
                    document = document,
                    antallVedlegg = 0,
                    ubesvartPublishedAt = null,
                    veilederIdent = veilederIdent,
                )
            }
        }
    }

    data class MeldingFraBehandler(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val msgId: String,
        override val type: MeldingType,
        override val conversationRef: UUID,
        override val innkommende: Boolean = true,
        override val tidspunkt: OffsetDateTime,
        override val parentRef: UUID?,
        override val tekst: String?,
        override val arbeidstakerPersonIdent: PersonIdent,
        override val behandlerPersonIdent: PersonIdent?,
        override val behandlerNavn: String?,
        override val behandlerRef: UUID? = null,
        override val antallVedlegg: Int,
        override val document: List<DocumentComponentDTO> = emptyList(),
        override val journalpostId: String? = null,
        override val veilederIdent: String? = null,
        val innkommendePublishedAt: OffsetDateTime?,
    ) : Melding(
        uuid,
        createdAt,
        msgId,
        type,
        conversationRef,
        innkommende,
        tidspunkt,
        parentRef,
        tekst,
        arbeidstakerPersonIdent,
        behandlerPersonIdent,
        behandlerNavn,
        behandlerRef,
        antallVedlegg,
        document,
        journalpostId,
        veilederIdent
    )

    enum class MeldingType {
        FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER,
        FORESPORSEL_PASIENT_LEGEERKLARING,
        FORESPORSEL_PASIENT_PAMINNELSE,
        HENVENDELSE_RETUR_LEGEERKLARING,
        HENVENDELSE_MELDING_FRA_NAV,
        HENVENDELSE_MELDING_TIL_NAV,
    }
}

fun hprNrWithNineDigits(hprnummer: Int): String =
    hprnummer.toString().padStart(9, '0')
