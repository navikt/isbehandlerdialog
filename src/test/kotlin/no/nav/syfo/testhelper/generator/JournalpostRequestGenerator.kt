package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.hprNrWithNineDigits
import no.nav.syfo.infrastructure.client.dokarkiv.domain.AvsenderMottaker
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Bruker
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrukerIdType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Dokument
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Dokumentvariant
import no.nav.syfo.infrastructure.client.dokarkiv.domain.FiltypeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.infrastructure.client.dokarkiv.domain.VariantformatType
import no.nav.syfo.testhelper.UserConstants

fun journalpostRequestGenerator(
    pdf: ByteArray,
    brevkodeType: BrevkodeType,
    tittel: String,
    eksternReferanseId: String,
    overstyrInnsynsregler: String?,
) = JournalpostRequest(
    avsenderMottaker = AvsenderMottaker.create(
        id = hprNrWithNineDigits(UserConstants.HPRID),
        idType = BrukerIdType.HPRNR,
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
