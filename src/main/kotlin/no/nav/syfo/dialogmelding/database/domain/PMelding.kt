package no.nav.syfo.dialogmelding.database.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.UUID

data class PMelding(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val innkommende: Boolean,
    val type: String,
    val conversation: UUID,
    val parent: UUID?,
    val tidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: PersonIdent,
    val behandlerPersonIdent: PersonIdent?,
    val behandlerRef: UUID?,
    val tekst: String?,
    val antallVedlegg: Int,
)
