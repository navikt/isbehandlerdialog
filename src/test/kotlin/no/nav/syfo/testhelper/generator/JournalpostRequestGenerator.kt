package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.testhelper.UserConstants

fun journalpostRequestGenerator(
    pdf: ByteArray,
    brevkodeType: BrevkodeType,
    tittel: String,
    eksternReferanseId: String,
    overstyrInnsynsregler: String?,
) = JournalpostRequest(
    avsenderMottaker = AvsenderMottaker.create(
        id = UserConstants.BEHANDLER_PERSONIDENT.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = UserConstants.BEHANDLER_NAVN,
    ),
    bruker = Bruker.create(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, BrukerIdType.PERSON_IDENT),
    tittel = tittel,
    dokumenter = listOf(
        Dokument.create(
            brevkode = brevkodeType,
            tittel = tittel,
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = tittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
    overstyrInnsynsregler = overstyrInnsynsregler,
    eksternReferanseId = eksternReferanseId,
)
