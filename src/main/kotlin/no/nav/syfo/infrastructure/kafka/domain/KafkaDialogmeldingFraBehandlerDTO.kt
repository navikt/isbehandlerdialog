package no.nav.syfo.infrastructure.kafka.domain

import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.domain.PersonIdent
import java.time.*
import java.util.UUID

const val KODEVERK_MELDING_TIL_NAV = "2.16.578.1.12.4.1.1.8128"
const val HENVENDELSE_OM_SYKEFRAVAR = "1"
const val HENVENDELSE_OM_PASIENT = "2"

data class KafkaDialogmeldingFraBehandlerDTO(
    val msgId: String,
    val msgType: String?,
    val navLogId: String,
    val mottattTidspunkt: LocalDateTime,
    val conversationRef: String?,
    val parentRef: String?,
    val personIdentPasient: String,
    val personIdentBehandler: String?,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorOrgName: String,
    val legehpr: String?,
    val dialogmelding: Dialogmelding,
    val antallVedlegg: Int,
    val fellesformatXML: String,
)

fun KafkaDialogmeldingFraBehandlerDTO.isHenvendelseTilNAV(): Boolean {
    val henvendelseFraLegeHenvendelse = dialogmelding.henvendelseFraLegeHenvendelse
    return henvendelseFraLegeHenvendelse != null &&
        henvendelseFraLegeHenvendelse.temaKode.kodeverkOID == KODEVERK_MELDING_TIL_NAV
}

fun KafkaDialogmeldingFraBehandlerDTO.toMeldingFraBehandler(
    type: MeldingType,
    conversationRef: UUID,
): MeldingFraBehandler {
    val tekst = if (type == MeldingType.HENVENDELSE_MELDING_TIL_NAV) {
        dialogmelding.henvendelseFraLegeHenvendelse?.tekstNotatInnhold
            ?: dialogmelding.foresporselFraSaksbehandlerForesporselSvar?.tekstNotatInnhold
    } else {
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar?.tekstNotatInnhold
            ?: dialogmelding.henvendelseFraLegeHenvendelse?.tekstNotatInnhold
    }
    return MeldingFraBehandler(
        uuid = UUID.randomUUID(),
        createdAt = OffsetDateTime.now(),
        type = type,
        conversationRef = conversationRef,
        parentRef = parentRef?.let {
            try {
                UUID.fromString(it)
            } catch (exc: IllegalArgumentException) {
                null
            }
        },
        msgId = msgId,
        tidspunkt = mottattTidspunkt.atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime(),
        arbeidstakerPersonIdent = PersonIdent(personIdentPasient),
        behandlerPersonIdent = personIdentBehandler?.let { PersonIdent(personIdentBehandler) },
        behandlerNavn = dialogmelding.navnHelsepersonell,
        tekst = tekst,
        antallVedlegg = antallVedlegg,
        innkommendePublishedAt = null,
    )
}

data class Dialogmelding(
    val id: String,
    val innkallingMoterespons: InnkallingMoterespons?,
    val foresporselFraSaksbehandlerForesporselSvar: ForesporselFraSaksbehandlerForesporselSvar?,
    val henvendelseFraLegeHenvendelse: HenvendelseFraLegeHenvendelse?,
    val navnHelsepersonell: String,
    val signaturDato: LocalDateTime,
)

data class InnkallingMoterespons(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String?,
    val dokIdNotat: String?,
    val foresporsel: Foresporsel?,
)

data class HenvendelseFraLegeHenvendelse(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String,
    val dokIdNotat: String?,
    val foresporsel: Foresporsel?,
    val rollerRelatertNotat: RollerRelatertNotat?,
)

data class TemaKode(
    val kodeverkOID: String,
    val dn: String,
    val v: String,
    val arenaNotatKategori: String,
    val arenaNotatKode: String,
    val arenaNotatTittel: String,
)

data class ForesporselFraSaksbehandlerForesporselSvar(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String,
    val dokIdNotat: String?,
    val datoNotat: LocalDateTime?,
)

data class Foresporsel(
    val typeForesp: TypeForesp,
    val sporsmal: String,
    val dokIdForesp: String?,
    val rollerRelatertNotat: RollerRelatertNotat?,
)

data class RollerRelatertNotat(
    val rolleNotat: RolleNotat?,
    val person: Person?,
    val helsepersonell: Helsepersonell?,
)

data class Helsepersonell(
    val givenName: String,
    val familyName: String,
)

data class Person(
    val givenName: String,
    val familyName: String,
)

data class RolleNotat(
    val s: String,
    val v: String,
)

data class TypeForesp(
    val dn: String,
    val s: String,
    val v: String,
)
