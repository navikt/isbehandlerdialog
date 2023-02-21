package no.nav.syfo.behandlerdialog.api.domain

import java.util.*

data class BehandlerdialogDTO(
    val personIdent: String,
    val behandlerRef: UUID,
    val tekst: String,
)
