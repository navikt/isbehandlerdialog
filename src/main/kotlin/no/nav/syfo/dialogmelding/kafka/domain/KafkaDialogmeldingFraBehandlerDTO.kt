package no.nav.syfo.dialogmelding.kafka.domain

import no.nav.syfo.dialogmelding.domain.MeldingFraBehandler
import no.nav.syfo.dialogmelding.domain.DialogmeldingType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.*
import java.util.UUID

data class KafkaDialogmeldingFromBehandlerDTO(
    val msgId: String,
    val msgType: String,
    val navLogId: String,
    val mottattTidspunkt: LocalDateTime,
    val conversationRef: String?,
    val parentRef: String?,
    val personIdentPasient: String,
    val personIdentBehandler: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorOrgName: String,
    val legehpr: String?,
    val dialogmelding: Dialogmelding,
    val antallVedlegg: Int,
    val fellesformatXML: String,
)

fun KafkaDialogmeldingFromBehandlerDTO.toMeldingFraBehandler() =
    MeldingFraBehandler(
        uuid = UUID.randomUUID(),
        createdAt = OffsetDateTime.now(),
        msgId = msgId,
        msgType = DialogmeldingType.valueOf(msgType),
        mottakId = navLogId,
        conversationRef = UUID.fromString(conversationRef),
        parentRef = UUID.fromString(parentRef),
        mottattTidspunkt = mottattTidspunkt.atZone(ZoneId.of("Europe/Oslo")).toOffsetDateTime(),
        arbeidstakerPersonIdent = PersonIdent(personIdentPasient),
        behandlerPersonIdent = PersonIdent(personIdentBehandler),
        behandlerHprId = legehpr,
        legekontorOrgnr = legekontorOrgNr?.let { Virksomhetsnummer(it) },
        legekontorHerId = legekontorHerId,
        legekontorNavn = legekontorOrgName,
        tekstNotatInnhold = dialogmelding.foresporselFraSaksbehandlerForesporselSvar?.tekstNotatInnhold,
        antallVedlegg = antallVedlegg,
    )

data class Dialogmelding(
    val id: String,
    val innkallingMoterespons: InnkallingMoterespons?,
    val foresporselFraSaksbehandlerForesporselSvar: ForesporselFraSaksbehandlerForesporselSvar?,
    val henvendelseFraLegeHenvendelse: HenvendelseFraLegeHenvendelse?,
    val navnHelsepersonell: String,
    val signaturDato: LocalDateTime
)

data class HenvendelseFraLegeHenvendelse(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String,
    val dokIdNotat: String?,
    val foresporsel: Foresporsel?,
    val rollerRelatertNotat: RollerRelatertNotat?
)

data class InnkallingMoterespons(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String?,
    val dokIdNotat: String?,
    val foresporsel: Foresporsel?
)

data class TemaKode(
    val kodeverkOID: String,
    val dn: String,
    val v: String,
    val arenaNotatKategori: String,
    val arenaNotatKode: String,
    val arenaNotatTittel: String
)

data class ForesporselFraSaksbehandlerForesporselSvar(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String,
    val dokIdNotat: String?,
    val datoNotat: LocalDateTime?
)

data class Foresporsel(
    val typeForesp: TypeForesp,
    val sporsmal: String,
    val dokIdForesp: String?,
    val rollerRelatertNotat: RollerRelatertNotat?
)

data class RollerRelatertNotat(
    val rolleNotat: RolleNotat?,
    val person: Person?,
    val helsepersonell: Helsepersonell?
)

data class Helsepersonell(
    val givenName: String,
    val familyName: String
)

data class Person(
    val givenName: String,
    val familyName: String
)

data class RolleNotat(
    val s: String,
    val v: String
)

data class TypeForesp(
    val dn: String,
    val s: String,
    val v: String
)
