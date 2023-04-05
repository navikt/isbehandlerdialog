package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.application.database.DatabaseInterface
import org.slf4j.LoggerFactory

class JournalforDialogmeldingCronjob(
    private val database: DatabaseInterface,
    private val dokarkivCLient: DokarkivClient,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed aktivitetskrav automatisk oppfylt processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()


        try {
            database.connection.use { connection ->

                    result.updated++

                connection.commit()
            }
        } catch (e: Exception) {
            log.error("Caught exception in journalfor dialogmelding job")
            result.failed++
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforDialogmeldingCronjob::class.java)
    }
}
