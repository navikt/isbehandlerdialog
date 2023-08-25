package no.nav.syfo.melding.domain

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingDTO
import no.nav.syfo.melding.kafka.domain.*
import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.toMeldingStatusDTO
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
        fun createReturAvLegeerklaring(
            opprinneligForesporselLegeerklaring: MeldingTilBehandler,
            innkommendeLegeerklaring: MeldingFraBehandler,
            veilederIdent: String,
            document: List<DocumentComponentDTO>,
            tekst: String,
        ): MeldingTilBehandler {
            val now = OffsetDateTime.now()
            return MeldingTilBehandler(
                uuid = UUID.randomUUID(),
                createdAt = now,
                type = MeldingType.HENVENDELSE_RETUR_LEGEERKLARING,
                conversationRef = opprinneligForesporselLegeerklaring.conversationRef,
                parentRef = innkommendeLegeerklaring.uuid,
                tidspunkt = now,
                arbeidstakerPersonIdent = opprinneligForesporselLegeerklaring.arbeidstakerPersonIdent,
                behandlerPersonIdent = opprinneligForesporselLegeerklaring.behandlerPersonIdent,
                behandlerNavn = opprinneligForesporselLegeerklaring.behandlerNavn,
                behandlerRef = opprinneligForesporselLegeerklaring.behandlerRef,
                tekst = tekst,
                document = document,
                antallVedlegg = 0, // TODO: Eventuell opprinnelig melding pdf som vedlegg?
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
)

private fun MeldingTilBehandler.getDialogmeldingKode(): DialogmeldingKode {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingKode.FORESPORSEL
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> DialogmeldingKode.PAMINNELSE_FORESPORSEL
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> DialogmeldingKode.RETUR_LEGEERKLARING
        MeldingType.HENVENDELSE_MELDING_FRA_NAV -> DialogmeldingKode.MELDING_FRA_NAV
    }
}

private fun MeldingTilBehandler.getDialogmeldingKodeverk(): DialogmeldingKodeverk {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingKodeverk.FORESPORSEL
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> DialogmeldingKodeverk.HENVENDELSE
    }
}

private fun MeldingTilBehandler.getDialogmeldingType(): DialogmeldingType {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_PAMINNELSE, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> DialogmeldingType.DIALOG_FORESPORSEL
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> DialogmeldingType.DIALOG_NOTAT
    }
}

private fun MeldingTilBehandler.getBrevKode(): BrevkodeType {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> BrevkodeType.FORESPORSEL_OM_PASIENT
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> BrevkodeType.FORESPORSEL_OM_PASIENT_PAMINNELSE
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> BrevkodeType.HENVENDELSE_RETUR_LEGEERKLARING
        MeldingType.HENVENDELSE_MELDING_FRA_NAV -> BrevkodeType.HENVENDELSE_MELDING_FRA_NAV
    }
}

fun MeldingTilBehandler.toJournalpostRequest(pdf: ByteArray) =
    JournalpostRequest(
        avsenderMottaker = createAvsenderMottaker(behandlerPersonIdent, behandlerNavn),
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
    )

fun MeldingTilBehandler.createTittel(): String {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> MeldingTittel.DIALOGMELDING_DEFAULT.value
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> MeldingTittel.DIALOGMELDING_PAMINNELSE.value
        MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> MeldingTittel.DIALOGMELDING_RETUR.value
    }
}

fun MeldingTilBehandler.createOverstyrInnsynsregler(): String? {
    return when (this.type) {
        MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER, MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING, MeldingType.HENVENDELSE_RETUR_LEGEERKLARING, MeldingType.HENVENDELSE_MELDING_FRA_NAV -> OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.value
        MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> null
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
    behandlerNavn: String?,
): AvsenderMottaker {
    return if (behandlerPersonIdent == null) {
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
