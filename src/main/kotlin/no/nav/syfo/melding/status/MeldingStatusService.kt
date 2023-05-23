package no.nav.syfo.melding.status

import no.nav.syfo.melding.status.database.getMeldingStatus
import no.nav.syfo.melding.status.database.toMeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatus
import java.sql.Connection

class MeldingStatusService {
    internal fun getMeldingStatus(
        meldingId: Int,
        connection: Connection,
    ): MeldingStatus? = connection.getMeldingStatus(meldingId = meldingId)?.toMeldingStatus()
}
