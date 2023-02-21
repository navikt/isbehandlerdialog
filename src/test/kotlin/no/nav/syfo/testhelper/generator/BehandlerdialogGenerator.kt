package no.nav.syfo.testhelper.generator

import no.nav.syfo.behandlerdialog.api.domain.BehandlerdialogDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.UserConstants
import java.util.UUID

fun generateBehandlerdialog(
    personident: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    behandlerRef: UUID = UUID.randomUUID(),
    tekst: String = "Dette er en melding"
) = BehandlerdialogDTO(
    personIdent = personident.value,
    behandlerRef = behandlerRef,
    tekst = tekst,
)
