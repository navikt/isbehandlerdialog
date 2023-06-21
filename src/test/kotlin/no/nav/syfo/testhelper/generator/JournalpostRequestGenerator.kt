package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.melding.domain.createOverstyrInnsynsregler
import no.nav.syfo.melding.domain.createTittel
import no.nav.syfo.testhelper.UserConstants

fun journalpostRequestGenerator(
    pdf: ByteArray,
    brevkodeType: BrevkodeType,
    isPaminnelse: Boolean,
) = JournalpostRequest(
    avsenderMottaker = AvsenderMottaker.create(
        id = UserConstants.BEHANDLER_PERSONIDENT.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = UserConstants.BEHANDLER_NAVN,
    ),
    bruker = Bruker.create(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, BrukerIdType.PERSON_IDENT),
    tittel = createTittel(isPaminnelse),
    dokumenter = listOf(
        Dokument.create(
            brevkode = brevkodeType,
            tittel = createTittel(isPaminnelse),
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = createTittel(isPaminnelse),
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
    overstyrInnsynsregler = createOverstyrInnsynsregler(!isPaminnelse),
)
