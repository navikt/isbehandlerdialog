package no.nav.syfo.dialogmelding.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.OffsetDateTime
import java.util.UUID

data class DialogmeldingIn(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val msgId: String,
    val msgType: DialogmeldingType,
    val mottakId: String,
    val conversationRef: UUID?,
    val parentRef: UUID?,
    val mottattTidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: PersonIdent,
    val behandlerPersonIdent: PersonIdent?,
    val behandlerHprId: String?,
    val legekontorOrgnr: Virksomhetsnummer?,
    val legekontorHerId: String?,
    val legekontorNavn: String?,
    val tekstNotatInnhold: String?,
    val antallVedlegg: Int,
)

enum class DialogmeldingType() {
    DIALOG_FORESPORSEL,
    DIALOG_NOTAT,
    OPPFOLGINGSPLAN,
}
