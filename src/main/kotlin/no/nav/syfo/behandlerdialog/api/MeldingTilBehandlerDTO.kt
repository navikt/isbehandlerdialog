package no.nav.syfo.behandlerdialog.api

import java.util.*

data class MeldingTilBehandlerDTO(
    val behandlerRef: UUID,
    val tekst: String,
)
