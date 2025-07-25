package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.kafka.legeerklaring.*
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDateTime
import java.util.*

fun generateKafkaLegeerklaringFraBehandlerDTO(
    behandlerPersonIdent: PersonIdent,
    behandlerNavn: String,
    personIdent: PersonIdent,
    msgId: String = UUID.randomUUID().toString(),
    conversationRef: String? = UUID.randomUUID().toString(),
    tidspunkt: LocalDateTime = LocalDateTime.now(),
    parentRef: String? = UUID.randomUUID().toString(),
) = LegeerklaringDTO(
    legeerklaering = generateLegeerklaring(
        personIdent = personIdent.value,
        behandlerNavn = behandlerNavn,
        tidspunkt = tidspunkt,
    ),
    personNrPasient = personIdent.value,
    personNrLege = behandlerPersonIdent.value,
    navLogId = UUID.randomUUID().toString(),
    msgId = msgId,
    legekontorOrgNr = "999999999",
    legekontorHerId = "${UserConstants.HERID}",
    legekontorOrgName = "Legekontoret",
    mottattDato = LocalDateTime.now(),
    conversationRef = conversationRef?.let {
        ConversationRef(
            refToParent = parentRef,
            refToConversation = conversationRef,
        )
    },
)

fun generateLegeerklaring(
    personIdent: String,
    behandlerNavn: String,
    tidspunkt: LocalDateTime = LocalDateTime.now(),
) = Legeerklaering(
    id = UUID.randomUUID().toString(),
    arbeidsvurderingVedSykefravaer = false,
    arbeidsavklaringspenger = false,
    yrkesrettetAttforing = false,
    uforepensjon = false,
    pasient = Pasient(
        fornavn = "Fornavn",
        mellomnavn = null,
        etternavn = "Etternavn",
        fnr = personIdent,
        adresse = null,
        navKontor = null,
        postnummer = null,
        poststed = null,
        yrke = null,
        arbeidsgiver = Arbeidsgiver(
            navn = null,
            adresse = null,
            poststed = null,
            postnummer = null,
        ),
    ),
    sykdomsopplysninger = Sykdomsopplysninger(
        hoveddiagnose = null,
        bidiagnose = emptyList(),
        arbeidsuforFra = null,
        sykdomshistorie = "",
        statusPresens = "",
        borNavKontoretVurdereOmDetErEnYrkesskade = false,
        yrkesSkadeDato = null,
    ),
    plan = null,
    forslagTilTiltak = ForslagTilTiltak(
        behov = false,
        kjopAvHelsetjenester = false,
        reisetilskudd = false,
        aktivSykmelding = false,
        hjelpemidlerArbeidsplassen = false,
        arbeidsavklaringspenger = false,
        friskmeldingTilArbeidsformidling = false,
        andreTiltak = null,
        naermereOpplysninger = "",
        tekst = "",
    ),
    funksjonsOgArbeidsevne = FunksjonsOgArbeidsevne(
        vurderingFunksjonsevne = null,
        inntektsgivendeArbeid = false,
        hjemmearbeidende = false,
        student = false,
        annetArbeid = "",
        kravTilArbeid = null,
        kanGjenopptaTidligereArbeid = false,
        kanGjenopptaTidligereArbeidNa = false,
        kanGjenopptaTidligereArbeidEtterBehandling = false,
        kanIkkeGjenopptaNaverendeArbeid = null,
        kanTaAnnetArbeid = false,
        kanTaAnnetArbeidNa = false,
        kanTaAnnetArbeidEtterBehandling = false,
        kanIkkeTaAnnetArbeid = null,
    ),
    prognose = Prognose(
        vilForbedreArbeidsevne = false,
        anslattVarighetSykdom = null,
        anslattVarighetFunksjonsnedsetting = null,
        anslattVarighetNedsattArbeidsevne = null,
    ),
    arsakssammenheng = null,
    andreOpplysninger = null,
    kontakt = Kontakt(
        skalKontakteBehandlendeLege = false,
        skalKontakteArbeidsgiver = false,
        skalKontakteBasisgruppe = false,
        kontakteAnnenInstans = null,
        onskesKopiAvVedtak = false,
    ),
    pasientenBurdeIkkeVite = null,
    tilbakeholdInnhold = false,
    signatur = Signatur(
        dato = tidspunkt,
        navn = behandlerNavn,
        adresse = null,
        postnummer = null,
        poststed = null,
        signatur = null,
        tlfNummer = null,
    ),
    signaturDato = tidspunkt,
)
