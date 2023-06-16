package no.nav.syfo.melding.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.UUID

interface Melding {
    val uuid: UUID
    val msgId: String?
    val type: MeldingType
    val conversationRef: UUID
    val innkommende: Boolean
    val tidspunkt: OffsetDateTime
    val parentRef: UUID?
    val tekst: String?
    val arbeidstakerPersonIdent: PersonIdent
    val behandlerPersonIdent: PersonIdent?
    val behandlerNavn: String?
    val behandlerRef: UUID?
    val antallVedlegg: Int
    val document: List<DocumentComponentDTO>
    val journalpostId: String?
    val veilederIdent: String?
}
