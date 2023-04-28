package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.testhelper.UserConstants

fun journalpostRequestGenerator(
    pdf: ByteArray
) = JournalpostRequest(
    avsenderMottaker = AvsenderMottaker.create(
        id = UserConstants.BEHANDLER_PERSONIDENT.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = UserConstants.BEHANDLER_NAVN,
    ),
    bruker = Bruker.create(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, BrukerIdType.PERSON_IDENT),
    tittel = "Dialogmelding til behandler",
    dokumenter = listOf(
        Dokument.create(
            brevkode = BrevkodeType.FORESPORSEL_OM_PASIENT,
            tittel = "Dialogmelding til behandler",
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = "Dialogmelding til behandler",
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
)
