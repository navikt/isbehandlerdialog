package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.Oppfolgingstilfelle

interface IOppfolgingstilfelleClient {
    suspend fun getOppfolgingstilfelle(personIdent: PersonIdent): Oppfolgingstilfelle?
}
