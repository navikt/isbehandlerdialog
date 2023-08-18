package no.nav.syfo.melding.kafka.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.domain.*
import java.time.*
import java.util.UUID

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

fun KafkaDialogmeldingFraBehandlerDTO.isForesporselSvarWithConversationRef() =
    isForesporselSvar() && !conversationRef.isNullOrEmpty()

fun KafkaDialogmeldingFraBehandlerDTO.isForesporselSvarWithoutConversationRef() =
    isForesporselSvar() && conversationRef.isNullOrEmpty()

fun KafkaDialogmeldingFraBehandlerDTO.isForesporselSvar() =
    msgType == DialogmeldingType.DIALOG_SVAR.name &&
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar?.temaKode?.v == DialogmeldingKode.SVAR_FORESPORSEL.value.toString()

fun KafkaDialogmeldingFraBehandlerDTO.toMeldingFraBehandler() =
    MeldingFraBehandler(
        uuid = UUID.randomUUID(),
        createdAt = OffsetDateTime.now(),
        type = MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER,
        conversationRef = conversationRef?.let {
            try {
                UUID.fromString(it)
            } catch (exc: IllegalArgumentException) {
                UUID.randomUUID()
            }
        } ?: UUID.randomUUID(),
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
        tekst = dialogmelding.foresporselFraSaksbehandlerForesporselSvar?.tekstNotatInnhold
            ?: dialogmelding.henvendelseFraLegeHenvendelse?.tekstNotatInnhold,
        antallVedlegg = antallVedlegg,
        innkommendePublishedAt = null,
    )

data class Dialogmelding(
    val id: String,
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
