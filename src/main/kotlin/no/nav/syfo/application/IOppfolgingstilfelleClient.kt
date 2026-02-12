package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Oppfolgingstilfelle

interface IOppfolgingstilfelleClient {
    suspend fun getOppfolgingstilfelle(personIdent: PersonIdent): Oppfolgingstilfelle?
}
