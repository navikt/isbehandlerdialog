package no.nav.syfo.behandlerdialog.api

import java.time.OffsetDateTime
import java.util.*

// Denne vil etterhvert være en slags gruppering på samtaleRef->meldinger
data class MeldingTilBehandlerResponseDTO(
    val behandlerRef: UUID,
    val tekst: String,
    val bestiltTidspunkt: OffsetDateTime,
)
