package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.domain.PMelding
import java.util.UUID

interface IMeldingRepository {
    suspend fun getMelding(uuid: UUID): PMelding?
}
