package no.nav.syfo.testhelper.generator

import no.nav.syfo.infrastructure.kafka.janitor.JanitorAction
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventDTO
import no.nav.syfo.testhelper.UserConstants
import java.util.UUID

fun generateJanitorEventDTO(
    action: String = JanitorAction.SLETT_BEHANDLERDIALOG.name,
    referenceUUID: String = UUID.randomUUID().toString(),
    personident: String = UserConstants.ARBEIDSTAKER_PERSONIDENT.value,
    navident: String = UserConstants.VEILEDER_IDENT,
    eventUUID: String = UUID.randomUUID().toString(),
) = JanitorEventDTO(
    referenceUUID = referenceUUID,
    navident = navident,
    eventUUID = eventUUID,
    personident = personident,
    action = action,
)
