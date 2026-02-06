package no.nav.syfo.domain

import java.time.OffsetDateTime
import java.util.*

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
