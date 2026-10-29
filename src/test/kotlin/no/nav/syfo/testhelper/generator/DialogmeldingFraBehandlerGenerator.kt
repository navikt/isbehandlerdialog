package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.kafka.domain.*
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDateTime
import java.util.*

fun generateDialogmeldingFraBehandlerDialogNotatDTO(
    uuid: UUID = UUID.randomUUID(),
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    conversationRef: String = UUID.randomUUID().toString(),
    antallVedlegg: Int = 0,
) = KafkaDialogmeldingFraBehandlerDTO(
    msgId = uuid.toString(),
    msgType = DialogmeldingType.DIALOG_NOTAT.name,
    navLogId = "1234asd123",
    mottattTidspunkt = LocalDateTime.now(),
    conversationRef = conversationRef,
    parentRef = UUID.randomUUID().toString(),
    personIdentPasient = personIdent.value,
    personIdentBehandler = UserConstants.BEHANDLER_PERSONIDENT.value,
    legekontorOrgNr = "987654321",
    legekontorHerId = "",
    legekontorOrgName = "",
    legehpr = UserConstants.HPRID.toString(),
    antallVedlegg = antallVedlegg,
    dialogmelding = Dialogmelding(
        id = uuid.toString(),
        henvendelseFraLegeHenvendelse = HenvendelseFraLegeHenvendelse(
            temaKode = TemaKode(KODEVERK_MELDING_TIL_NAV, "Henvendelse om sykefraværsoppfølging", HENVENDELSE_OM_SYKEFRAVAR, "", "", ""),
            tekstNotatInnhold = "Dette er innholdet i et notat",
            dokIdNotat = null,
            foresporsel = null,
            rollerRelatertNotat = null,
        ),
        navnHelsepersonell = UserConstants.BEHANDLER_NAVN,
        signaturDato = LocalDateTime.now(),
        foresporselFraSaksbehandlerForesporselSvar = null,
        innkallingMoterespons = null,
    )
)

fun generateDialogmeldingFraBehandlerDialogNotatIkkeSykefravrDTO(
    uuid: UUID = UUID.randomUUID(),
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT_INACTIVE,
    conversationRef: String = UUID.randomUUID().toString(),
    antallVedlegg: Int = 0,
) = KafkaDialogmeldingFraBehandlerDTO(
    msgId = uuid.toString(),
    msgType = DialogmeldingType.DIALOG_NOTAT.name,
    navLogId = "1234asd123",
    mottattTidspunkt = LocalDateTime.now(),
    conversationRef = conversationRef,
    parentRef = UUID.randomUUID().toString(),
    personIdentPasient = personIdent.value,
    personIdentBehandler = UserConstants.BEHANDLER_PERSONIDENT.value,
    legekontorOrgNr = "987654321",
    legekontorHerId = "",
    legekontorOrgName = "",
    legehpr = UserConstants.HPRID.toString(),
    antallVedlegg = antallVedlegg,
    dialogmelding = Dialogmelding(
        id = uuid.toString(),
        henvendelseFraLegeHenvendelse = HenvendelseFraLegeHenvendelse(
            temaKode = TemaKode(KODEVERK_MELDING_TIL_NAV, "Henvendelse om pasient", HENVENDELSE_OM_PASIENT, "", "", ""),
            tekstNotatInnhold = "Dette er innholdet i et notat",
            dokIdNotat = null,
            foresporsel = null,
            rollerRelatertNotat = null,
        ),
        navnHelsepersonell = UserConstants.BEHANDLER_NAVN,
        signaturDato = LocalDateTime.now(),
        foresporselFraSaksbehandlerForesporselSvar = null,
        innkallingMoterespons = null,
    )
)

fun generateDialogmeldingFraBehandlerForesporselSvarDTO(
    uuid: UUID = UUID.randomUUID(),
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    conversationRef: String = UUID.randomUUID().toString(),
    parentRef: String = UUID.randomUUID().toString(),
    kodeverk: String = "2.16.578.1.12.4.1.1.9069",
    kodeTekst: String = "Svar på forespørsel",
    kode: String = "5",
    antallVedlegg: Int = 0,
) = KafkaDialogmeldingFraBehandlerDTO(
    msgId = uuid.toString(),
    msgType = DialogmeldingType.DIALOG_SVAR.name,
    navLogId = "1234asd123",
    mottattTidspunkt = LocalDateTime.now(),
    conversationRef = conversationRef,
    parentRef = parentRef,
    personIdentPasient = personIdent.value,
    personIdentBehandler = UserConstants.BEHANDLER_PERSONIDENT.value,
    legekontorOrgNr = "987654321",
    legekontorHerId = "",
    legekontorOrgName = "",
    legehpr = UserConstants.HPRID.toString(),
    antallVedlegg = antallVedlegg,
    dialogmelding = Dialogmelding(
        id = uuid.toString(),
        henvendelseFraLegeHenvendelse = null,
        navnHelsepersonell = UserConstants.BEHANDLER_NAVN,
        signaturDato = LocalDateTime.now(),
        foresporselFraSaksbehandlerForesporselSvar = ForesporselFraSaksbehandlerForesporselSvar(
            temaKode = TemaKode(kodeverk, kodeTekst, kode, "", "", ""),
            datoNotat = LocalDateTime.now(),
            dokIdNotat = null,
            tekstNotatInnhold = "Dette er innholdet i et notat"
        ),
        innkallingMoterespons = null,
    )
)
