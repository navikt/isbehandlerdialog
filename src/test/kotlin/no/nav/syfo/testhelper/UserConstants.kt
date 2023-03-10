package no.nav.syfo.testhelper

import no.nav.syfo.domain.*

object UserConstants {

    private const val ARBEIDSTAKER_FNR = "12345678912"
    private const val VIRKSOMHETSNUMMER = "123456789"

    val ARBEIDSTAKER_PERSONIDENT = PersonIdent(ARBEIDSTAKER_FNR)
    val PERSONIDENT_VEILEDER_NO_ACCESS = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("3", "1"))

    val VIRKSOMHETSNUMMER_DEFAULT = Virksomhetsnummer(VIRKSOMHETSNUMMER)
    const val VEILEDER_IDENT = "Z999999"

    val FASTLEGE_FNR = PersonIdent("12125678911")
    val PARTNERID = PartnerId(321)
    const val HERID = 404
    const val HPRID = 1337
}
