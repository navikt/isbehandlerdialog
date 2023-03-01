package no.nav.syfo.dialogmelding.database.domain

import java.time.OffsetDateTime
import java.util.UUID

data class PMelding(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val innkommende: Boolean,
    val type: String,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val tidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: String,
    val behandlerPersonIdent: String?,
    val behandlerRef: UUID?,
    val tekst: String?,
    val antallVedlegg: Int,
)
