package no.nav.syfo.client.dokarkiv.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer

const val JOURNALFORENDE_ENHET = 9999

enum class JournalpostType(
    val value: String,
) {
    UTGAAENDE("UTGAAENDE"),
}

enum class JournalpostTema(
    val value: String,
) {
    OPPFOLGING("OPP"),
}

enum class JournalpostKanal(
    val value: String,
) {
    DITT_NAV("NAV_NO"),
    SENTRAL_UTSKRIFT("S"),
    HELSENETTET("HELSENETTET"),
}

data class JournalpostRequest private constructor(
    val avsenderMottaker: AvsenderMottaker,
    val tittel: String,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: Int?,
    val journalpostType: String,
    val tema: String,
    val kanal: String,
    val sak: Sak,
) {
    companion object {
        fun create(
            avsenderMottaker: AvsenderMottaker,
            tittel: String,
            bruker: Bruker? = null,
            dokumenter: List<Dokument>,
            journalfoerendeEnhet: Int?,
            journalpostType: JournalpostType,
            tema: JournalpostTema,
            kanal: JournalpostKanal,
            sak: Sak,
        ) = JournalpostRequest(
            avsenderMottaker = avsenderMottaker,
            tittel = tittel,
            bruker = bruker,
            dokumenter = dokumenter,
            journalfoerendeEnhet = journalfoerendeEnhet,
            journalpostType = journalpostType.value,
            tema = tema.value,
            kanal = kanal.value,
            sak = sak,
        )
    }
}

fun createJournalpostRequest(
    brukerPersonIdent: PersonIdent,
    mottakerPersonIdent: PersonIdent? = null,
    mottakerVirksomhetsnummer: Virksomhetsnummer? = null,
    mottakerNavn: String,
    brevkodeType: BrevkodeType,
    digitalt: Boolean,
    dokumentName: String,
    dokumentPdf: ByteArray,
    kanal: JournalpostKanal? = null,
): JournalpostRequest {
    val avsenderMottaker = AvsenderMottaker.create(
        id = mottakerPersonIdent?.value ?: mottakerVirksomhetsnummer?.value,
        idType = mottakerPersonIdent?.let {
            BrukerIdType.PERSON_IDENT
        } ?: mottakerVirksomhetsnummer?.let {
            BrukerIdType.VIRKSOMHETSNUMMER
        },
        navn = mottakerNavn,
    )
    val bruker = Bruker.create(
        id = brukerPersonIdent.value,
        idType = BrukerIdType.PERSON_IDENT,
    )
    val jpKanal = kanal ?: if (digitalt) {
        JournalpostKanal.DITT_NAV
    } else {
        JournalpostKanal.SENTRAL_UTSKRIFT
    }
    val sak = Sak.invoke(
        sakstype = SaksType.GENERELL,
    )
    val dokumenter = createDokumentList(
        brevkodeType = brevkodeType,
        dokumentNavn = dokumentName,
        dokumentPdf = dokumentPdf,
    )
    return JournalpostRequest.create(
        avsenderMottaker = avsenderMottaker,
        tittel = dokumentName,
        bruker = bruker,
        dokumenter = dokumenter,
        journalfoerendeEnhet = JOURNALFORENDE_ENHET,
        journalpostType = JournalpostType.UTGAAENDE,
        kanal = jpKanal,
        sak = sak,
        tema = JournalpostTema.OPPFOLGING,
    )
}

private fun createDokumentList(
    brevkodeType: BrevkodeType,
    dokumentNavn: String,
    dokumentPdf: ByteArray,
): List<Dokument> {
    val dokumentvariantList = listOf(
        Dokumentvariant.create(
            filnavn = dokumentNavn,
            filtype = FiltypeType.PDFA,
            fysiskDokument = dokumentPdf,
            variantformat = VariantformatType.ARKIV,
        )
    )
    return listOf(
        Dokument.create(
            brevkode = brevkodeType,
            dokumentvarianter = dokumentvariantList,
            tittel = dokumentNavn,
        )
    )
}
