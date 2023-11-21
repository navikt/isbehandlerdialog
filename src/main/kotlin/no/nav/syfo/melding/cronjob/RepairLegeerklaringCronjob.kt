package no.nav.syfo.melding.cronjob

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.database.toPMelding
import no.nav.syfo.melding.kafka.config.mapper
import no.nav.syfo.melding.kafka.legeerklaring.LegeerklaringDTO
import org.slf4j.LoggerFactory

class RepairLegeerklaringCronjob(
    val database: DatabaseInterface,
    private val bucketName: String,
    private val pdfgenClient: PdfGenClient,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 24 * 60 * 7L

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed repair legeerklaring job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()
        val legeerklaringFraBehandler = database.getMeldingToRepair()
        val storage = StorageOptions.newBuilder().build().service

        legeerklaringFraBehandler.forEach { legeerklaring ->
            try {
                val dto = getLegeerklaring(storage, legeerklaring.msgId!!)
                val pdf = getPDF(dto)
                database.updatePdf(legeerklaring.id, pdf)
                result.updated++
            } catch (e: Exception) {
                log.error("Caught exception in repair legeerklaring", e)
                result.failed++
            }
        }

        return result
    }

    private fun DatabaseInterface.getMeldingToRepair(): List<PMelding> {
        return connection.use { connection ->
            connection.prepareStatement(
                """
                select m.* from melding m inner join vedlegg v on (v.melding_id=m.id) where innkommende 
                and m.type='FORESPORSEL_PASIENT_LEGEERKLARING'
                and m.created_at > '2023-11-20T07:00:00' 
                and m.created_at < '2023-11-20T09:05:00'
                and length(v.pdf) = 481947
               """
            ).use { ps ->
                ps.executeQuery().toList { toPMelding() }
            }
        }
    }

    private fun DatabaseInterface.updatePdf(
        meldingId: PMelding.Id,
        pdf: ByteArray,
    ) {
        return connection.use { connection ->
            connection.prepareStatement(
                "update vedlegg set pdf=? where melding_id=? and number=0"
            ).use { ps ->
                ps.setBytes(1, pdf)
                ps.setInt(2, meldingId.id)
                val updateCount = ps.executeUpdate()
                if (updateCount != 1) {
                    throw RuntimeException("Unexpected update count")
                }
            }
            connection.prepareStatement(
                "update melding set innkommende_published_at=null where id=?"
            ).use { ps ->
                ps.setInt(1, meldingId.id)
                val updateCount = ps.executeUpdate()
                if (updateCount != 1) {
                    throw RuntimeException("Unexpected update count")
                }
            }
            connection.commit()
        }
    }

    private fun getLegeerklaring(
        storage: Storage,
        objectId: String
    ): LegeerklaringDTO =
        storage.get(bucketName, objectId)?.let { blob ->
            mapper.readValue(blob.getContent())
        } ?: throw RuntimeException("Fant ikke legeerklaring i gcp bucket: $objectId")

    private fun getPDF(
        dto: LegeerklaringDTO,
    ) = runBlocking {
        pdfgenClient.generateLegeerklaring(dto)!!
    }

    companion object {
        private val log = LoggerFactory.getLogger(RepairLegeerklaringCronjob::class.java)
    }
}
