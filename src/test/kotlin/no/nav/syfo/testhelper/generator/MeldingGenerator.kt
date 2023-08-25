package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingTilBehandlerRequestDTO
import no.nav.syfo.melding.domain.DocumentComponentDTO
import no.nav.syfo.melding.domain.DocumentComponentType
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.domain.toMeldingFraBehandler
import no.nav.syfo.testhelper.UserConstants
import java.util.UUID

fun generateMeldingTilBehandlerRequestDTO(
    behandlerRef: UUID = UUID.randomUUID(),
    tekst: String = "Melding til behandler",
    type: MeldingType = MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER,
) = MeldingTilBehandlerRequestDTO(
    type = type,
    behandlerRef = behandlerRef,
    behandlerIdent = UserConstants.BEHANDLER_PERSONIDENT.value,
    behandlerNavn = UserConstants.BEHANDLER_NAVN,
    tekst = tekst,
    document = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.HEADER_H1,
            title = null,
            texts = listOf("Dialogmelding"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = null,
            texts = listOf(tekst),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            key = "Standardtekst",
            title = null,
            texts = listOf("Dette er en standardtekst"),
        ),
    )
)

fun generateMeldingFraBehandler(
    conversationRef: UUID = UUID.randomUUID(),
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    tekst: String = "Melding fra behandler",
    msgId: UUID = UUID.randomUUID(),
) = generateDialogmeldingFraBehandlerDTO(
    uuid = msgId,
    personIdent = personIdent,
).toMeldingFraBehandler().copy(
    conversationRef = conversationRef,
    tekst = tekst,
)

fun generateMeldingTilBehandler(
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    behandlerRef: UUID = UUID.randomUUID(),
    tekst: String = "Melding til behandler",
    type: MeldingType = MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER,
    veilederIdent: String = UserConstants.VEILEDER_IDENT,
): MeldingTilBehandler {
    val meldingTilBehandlerRequestDTO = generateMeldingTilBehandlerRequestDTO(
        behandlerRef = behandlerRef,
        tekst = tekst,
        type = type,
    )
    return MeldingTilBehandler.createMeldingTilBehandler(
        type = type,
        veilederIdent = veilederIdent,
        personIdent = personIdent,
        document = meldingTilBehandlerRequestDTO.document,
        behandlerRef = meldingTilBehandlerRequestDTO.behandlerRef,
        behandlerNavn = meldingTilBehandlerRequestDTO.behandlerNavn,
        behandlerIdent = meldingTilBehandlerRequestDTO.behandlerIdent,
        tekst = meldingTilBehandlerRequestDTO.tekst,
    )
}

val defaultMeldingTilBehandler = generateMeldingTilBehandler()
val defaultMeldingFraBehandler = generateMeldingFraBehandler()
