package no.nav.syfo.testhelper.generator

import no.nav.syfo.behandlerdialog.api.MeldingTilBehandlerRequestDTO
import java.util.UUID

fun generateMeldingTilBehandlerRequestDTO(
    behandlerRef: UUID = UUID.randomUUID(),
    tekst: String = "Dette er en melding",
) = MeldingTilBehandlerRequestDTO(
    behandlerRef = behandlerRef,
    tekst = tekst,
)
