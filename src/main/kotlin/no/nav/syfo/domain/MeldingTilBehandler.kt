package no.nav.syfo.domain

import no.nav.syfo.api.models.MeldingDTO
import no.nav.syfo.infrastructure.client.dokarkiv.domain.*
import no.nav.syfo.infrastructure.kafka.domain.*
import java.time.OffsetDateTime
import java.util.*

data class MeldingTilBehandler(
    override val uuid: UUID,
    val createdAt: OffsetDateTime,
    override val type: MeldingType,
    override val conversationRef: UUID,
    override val parentRef: UUID?,
    override val tidspunkt: OffsetDateTime,
    override val arbeidstakerPersonIdent: PersonIdent,
    override val behandlerPersonIdent: PersonIdent?,
    override val behandlerNavn: String?,
    override val behandlerRef: UUID,
    override val tekst: String,
    override val document: List<DocumentComponentDTO>,
    override val antallVedlegg: Int,
    val ubesvartPublishedAt: OffsetDateTime?,
    override val veilederIdent: String?,
) : Melding {
    override val msgId: String? = null
    override val innkommende: Boolean = false
    override val journalpostId: String? = null

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

fun MeldingTilBehandler.toMeldingDTO(status: MeldingStatus?) = MeldingDTO(
    uuid = uuid,
    conversationRef = conversationRef,
    parentRef = parentRef,
    behandlerRef = behandlerRef,
    behandlerNavn = null,
    tekst = tekst,
    document = document,
    tidspunkt = tidspunkt,
    innkommende = false,
    type = type,
    antallVedlegg = antallVedlegg,
    status = status?.toMeldingStatusDTO(),
    veilederIdent = veilederIdent,
    isFirstVedleggLegeerklaring = false,
)

fun MeldingTilBehandler.toDialogmeldingBestillingDTO(meldingPdf: ByteArray) = DialogmeldingBestillingDTO(
    behandlerRef = this.behandlerRef.toString(),
    personIdent = this.arbeidstakerPersonIdent.value,
    dialogmeldingUuid = this.uuid.toString(),
    dialogmeldingRefParent = this.parentRef?.toString(),
    dialogmeldingRefConversation = this.conversationRef.toString(),
    dialogmeldingType = this.getDialogmeldingType().name,
    dialogmeldingKodeverk = this.getDialogmeldingKodeverk().name,
    dialogmeldingKode = this.getDialogmeldingKode().value,
    dialogmeldingTekst = this.document.serialize(),
    dialogmeldingVedlegg = meldingPdf,
    kilde = "SYFO",
)

private fun MeldingTilBehandler.getDialogmeldingKode(): DialogmeldingKode {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingKode.FORESPORSEL
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> DialogmeldingKode.PAMINNELSE_FORESPORSEL
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> DialogmeldingKode.RETUR_LEGEERKLARING
        MeldingType.HENVENDELSE_MELDING_FRA_NAV -> DialogmeldingKode.MELDING_FRA_NAV
        MeldingType.HENVENDELSE_MELDING_TIL_NAV -> DialogmeldingKode.HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING
    }
}

private fun MeldingTilBehandler.getDialogmeldingKodeverk(): DialogmeldingKodeverk {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingKodeverk.FORESPORSEL
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> DialogmeldingKodeverk.HENVENDELSE
    }
}

private fun MeldingTilBehandler.getDialogmeldingType(): DialogmeldingType {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingType.DIALOG_FORESPORSEL
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> DialogmeldingType.DIALOG_NOTAT
    }
}

private fun MeldingTilBehandler.getBrevKode(): BrevkodeType {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> BrevkodeType.FORESPORSEL_OM_PASIENT
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> BrevkodeType.FORESPORSEL_OM_PASIENT_PAMINNELSE
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> BrevkodeType.HENVENDELSE_RETUR_LEGEERKLARING
        MeldingType.HENVENDELSE_MELDING_FRA_NAV -> BrevkodeType.HENVENDELSE_MELDING_FRA_NAV
        MeldingType.HENVENDELSE_MELDING_TIL_NAV -> BrevkodeType.HENVENDELSE_MELDING_TIL_NAV
    }
}

private fun MeldingTilBehandler.kanHaPaminnelse(): Boolean = when (this.type) {
    MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> true
    MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.HENVENDELSE_MELDING_FRA_NAV, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> false
}

fun MeldingTilBehandler.toJournalpostRequest(
    behandlerHprId: Int?,
    pdf: ByteArray,
) = JournalpostRequest(
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

fun MeldingTilBehandler.createTittel(): String {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> MeldingTittel.DIALOGMELDING_DEFAULT.value
        MeldingType.HENVENDELSE_MELDING_TIL_NAV -> MeldingTittel.DIALOGMELDING_TIL_NAV.value
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> MeldingTittel.DIALOGMELDING_PAMINNELSE.value
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> MeldingTittel.DIALOGMELDING_RETUR.value
    }
}

fun MeldingTilBehandler.createOverstyrInnsynsregler(): String? {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.value
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.HENVENDELSE_MELDING_TIL_NAV -> null
    }
}

fun MeldingTilBehandler.toKafkaMeldingDTO() = KafkaMeldingDTO(
    uuid = uuid.toString(),
    personIdent = arbeidstakerPersonIdent.value,
    type = type.name,
    conversationRef = conversationRef.toString(),
    parentRef = parentRef?.toString(),
    msgId = msgId,
    tidspunkt = tidspunkt,
    behandlerPersonIdent = behandlerPersonIdent?.value,
)

fun createAvsenderMottaker(
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

fun hprNrWithNineDigits(hprnummer: Int): String {
    return hprnummer.toString().padStart(9, '0')
}
