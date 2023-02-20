package no.nav.syfo.testhelper

import no.nav.syfo.dialogmelding.kafka.KafkaDialogmeldingFromBehandlerDTO
import java.time.LocalDateTime
import java.util.*

val fellesformatXML = """<?xml version="1.0" ?>
<EI_fellesformat xmlns="http://www.nav.no/xml/eiff/2/" >
    <MsgHead xmlns="http://www.kith.no/xmlstds/msghead/2006-05-24">
        <Type DN="Notat" V="DIALOG_NOTAT" />
        <MIGversion>v1.2 2006-05-24</MIGversion>
        <GenDate>2019-01-16T22:51:35.5317672+01:00</GenDate>
        <MsgId>37340D30-FE14-42B5-985F-A8FF8FFA0CB5</MsgId>
        <ConversationRef>
            <RefToConversation>37340D30-FE14-42B5-985F-A8FF8FFA0C99</RefToConversation>
            <RefToParent>37340D30-FE14-42B5-985F-A8FF8FFA0CB5</RefToParent>
        </ConversationRef>
        <Ack DN="Ja" V="J" />
        <Sender>
            <Organisation>
                <OrganisationName>Kule helsetjenester AS</OrganisationName>
                <Ident>
                    <Id>223456789</Id>
                    <TypeId DN="Organisasjonsnummeret i Enhetsregister (Brønnøysund)" S="1.16.578.1.12.3.1.1.9051" V="ENH" />
                </Ident>
                <Ident>
                    <Id>0123</Id>
                    <TypeId DN="Identifikator fra Helsetjenesteenhetsregisteret (HER-id)" V="HER" S="1.23.456.7.89.1.2.3.4567.8912" />
                </Ident>
                <Address>
                    <StreetAdr>Oppdiktet gate 203</StreetAdr>
                    <PostalCode>1234</PostalCode>
                    <City>Oslo</City>
                </Address>
                <Organisation/>
                <HealthcareProfessional>
                    <Ident>
                        <Id>${UserConstants.FASTLEGE_FNR.value}</Id>
                        <TypeId V="FNR" S="2.16.578.1.12.4.1.1.8116" DN="Fødselsnummer Norsk fødselsnummer"/>
                    </Ident>
                    <Ident>
                        <Id>${UserConstants.HPRID}</Id>
                        <TypeId V="HPR" S="2.16.578.1.12.4.1.1.8116" DN="HPR-nummer"/>
                    </Ident>
                    <Ident>
                        <Id>${UserConstants.HERID}</Id>
                        <TypeId V="HER" S="2.16.578.1.12.4.1.1.8116" DN="Identifikator fra Helsetjenesteenhetsregisteret"/>
                    </Ident>
                </HealthcareProfessional>
            </Organisation>
        </Sender>
        <Receiver>
            <Organisation>
                <OrganisationName>NAV</OrganisationName>
                <Ident>
                    <Id>889640782</Id>
                    <TypeId DN="Organisasjonsnummeret i Enhetsregister (Brønnøysund)" S="2.16.578.1.12.4.1.1.9051" V="ENH" />
                </Ident>
                <Ident>
                    <Id>79768</Id>
                    <TypeId DN="Identifikator fra Helsetjenesteenhetsregisteret (HER-id)" S="2.16.578.1.12.4.1.1.9051" V="HER" />
                </Ident>
            </Organisation>
        </Receiver>
        <Patient>
            <FamilyName>Test</FamilyName>
            <GivenName>Etternavn</GivenName>
            <DateOfBirth>1991-12-4</DateOfBirth>
            <Sex DN="Mann" V="1" />
            <Ident>
                <Id>${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}</Id>
                <TypeId DN="Fødselsnummer" S="2.16.578.1.12.4.1.1.8116" V="FNR" />
            </Ident>
            <Address>
                <Type DN="Postadresse" V="PST" />
                <StreetAdr>Sannergata 2</StreetAdr>
                <PostalCode>0655</PostalCode>
                <City>OSLO</City>
                <County DN="OSLO" V="0712" />
            </Address>
        </Patient>
    </MsgHead>
    <MottakenhetBlokk avsender="12312341" avsenderFnrFraDigSignatur="${UserConstants.FASTLEGE_FNR.value}" avsenderRef="SERIALNUMBER=996871045, CN=LEGEHUSET NOVA DA, O=LEGEHUSET NOVA DA, C=NO" ebAction="Henvendelse" ebRole="Sykmelder" ebService="HenvendelseFraLege" ebXMLSamtaleId="615356d4-f5e6-4138-a868-bbb63bd6195d" ediLoggId="1901162157lege21826.1" herIdentifikator="" meldingsType="xml" mottattDatotid="2019-01-16T21:57:43" partnerReferanse="${UserConstants.PARTNERID}" />
</EI_fellesformat>"""

fun generateDialogmeldingFromBehandlerDTO(uuid: UUID) = KafkaDialogmeldingFromBehandlerDTO(
    msgId = uuid.toString(),
    navLogId = "1234asd123",
    mottattTidspunkt = LocalDateTime.now(),
    personIdentPasient = "",
    personIdentBehandler = UserConstants.FASTLEGE_FNR.value,
    legekontorOrgNr = "",
    legekontorHerId = "",
    legekontorOrgName = "",
    legehpr = UserConstants.HPRID.toString(),
    fellesformatXML = fellesformatXML,
)
