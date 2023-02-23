package no.nav.syfo.testhelper.generator

import no.nav.syfo.behandlerdialog.api.MeldingTilBehandlerDTO
import java.util.UUID

fun generateMeldingTilBehandler(
    behandlerRef: UUID = UUID.randomUUID(),
    tekst: String = "Dette er en melding"
) = MeldingTilBehandlerDTO(
    behandlerRef = behandlerRef,
    tekst = tekst,
)
