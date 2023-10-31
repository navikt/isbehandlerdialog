package no.nav.syfo.testhelper

import no.nav.syfo.domain.*
import java.util.UUID

object UserConstants {

    private const val ARBEIDSTAKER_FNR = "12345678912"

    val ARBEIDSTAKER_PERSONIDENT = PersonIdent(ARBEIDSTAKER_FNR)
    val ARBEIDSTAKER_PERSONIDENT_INACTIVE = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("4", "1"))
    val PERSONIDENT_VEILEDER_NO_ACCESS = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("3", "1"))

    var PDF_FORESPORSEL_OM_PASIENT_TILLEGGSOPPLYSNINGER = byteArrayOf(0x2E, 0x28)
    var PDF_FORESPORSEL_OM_PASIENT_LEGEERKLARING = byteArrayOf(0x2E, 50)
    var PDF_FORESPORSEL_OM_PASIENT_PAMINNELSE = byteArrayOf(0x2E, 100)
    var PDF_LEGEERKLARING = byteArrayOf(0x2E, 101)
    var PDF_RETUR_LEGEERKLARING = byteArrayOf(0x2E, 102)
    var PDF_HENVENDELSE_MELDING_FRA_NAV = byteArrayOf(0x2E, 103)

    const val VEILEDER_IDENT = "Z999999"

    val MSG_ID_WITH_VEDLEGG = UUID.randomUUID()
    val VEDLEGG_BYTEARRAY = byteArrayOf(0x2E, 0x28)

    val BEHANDLER_PERSONIDENT = PersonIdent("12125678911")
    val BEHANDLER_NAVN = "Anne Legesen"
    val PARTNERID = PartnerId(321)
    const val HERID = 404
    const val HPRID = 1337
    const val VIRKSOMHETSNUMMER = "987654321"
}
