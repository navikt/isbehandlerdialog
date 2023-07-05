package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.kafka.legeerklaring.*
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDateTime
import java.util.UUID

fun generateKafkaLegeerklaringFraBehandlerDTO(
    behandlerPersonIdent: PersonIdent,
    behandlerNavn: String,
    personIdent: PersonIdent,
    msgId: String = UUID.randomUUID().toString(),
    conversationRef: String = UUID.randomUUID().toString(),
) = KafkaLegeerklaringDTO(
    legeerklaering = generateLegeerklaring(
        personIdent = personIdent.value,
        behandlerNavn = behandlerNavn,
    ),
    personNrPasient = personIdent.value,
    personNrLege = behandlerPersonIdent.value,
    navLogId = UUID.randomUUID().toString(),
    msgId = msgId,
    legekontorOrgNr = "999999999",
    legekontorHerId = "${UserConstants.HERID}",
    legekontorOrgName = "Legekontoret",
    mottattDato = LocalDateTime.now(),
    conversationRef = ConversationRef(
        refToParent = UUID.randomUUID().toString(),
        refToConversation = conversationRef,
    ),
)

fun generateLegeerklaring(
    personIdent: String,
    behandlerNavn: String,
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
        dato = LocalDateTime.now(),
        navn = behandlerNavn,
        adresse = null,
        postnummer = null,
        poststed = null,
        signatur = null,
        tlfNummer = null,
    ),
    signaturDato = LocalDateTime.now(),
)
