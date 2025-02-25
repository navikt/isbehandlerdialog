package no.nav.syfo.melding.kafka.dialogmelding

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.oppfolgingstilfelle.isActive
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.client.padm2.VedleggDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.domain.*
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.*

class KafkaDialogmeldingFraBehandlerConsumer(
    private val database: DatabaseInterface,
    private val padm2Client: Padm2Client,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
) : KafkaConsumerService<KafkaDialogmeldingFraBehandlerDTO> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processConsumerRecords(
                consumerRecords = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processConsumerRecords(
        consumerRecords: ConsumerRecords<String, KafkaDialogmeldingFraBehandlerDTO>,
    ) {
        database.connection.use { connection ->
            consumerRecords.forEach {
                COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_READ.increment()
                val kafkaDialogmeldingFraBehandler = it.value()
                if (kafkaDialogmeldingFraBehandler != null) {
                    if (kafkaDialogmeldingFraBehandler.dialogmelding.innkallingMoterespons == null) {
                        handleDialogmeldingFromBehandler(
                            kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                            connection = connection,
                        )
                    } // else: dialogmøterelaterte meldinger konsumeres av isdialogmote
                } else {
                    COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_TOMBSTONE.increment()
                    log.warn("Received kafkaDialogmeldingFraBehandler with no value: could be tombstone")
                }
            }
            connection.commit()
        }
    }

    private fun handleDialogmeldingFromBehandler(
        kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        connection: Connection,
    ) {
        val conversationRef = kafkaDialogmeldingFraBehandler.conversationRef
        val conversationRefUuid = if (conversationRef.isNullOrBlank()) { null } else {
            try { UUID.fromString(conversationRef) } catch (e: IllegalArgumentException) { null }
        }
        val utgaendeMelding = findUtgaendeMelding(
            kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
            conversationRef = conversationRefUuid,
            connection = connection,
        )
        if (utgaendeMelding != null) {
            storeDialogmeldingFromBehandler(
                connection = connection,
                kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                type = MeldingType.valueOf(utgaendeMelding.type),
                conversationRef = utgaendeMelding.conversationRef,
            )
        } else if (kafkaDialogmeldingFraBehandler.isHenvendelseTilNAV()) {
            handleHenvendelseTilNAV(
                connection = connection,
                kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                conversationRef = conversationRefUuid,
            )
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FOR_MODIA.increment()
            log.info(
                """
                    Received dialogmelding from behandler, but skipped since not for Modia
                    msgId: ${kafkaDialogmeldingFraBehandler.msgId}
                    conversationRef: ${kafkaDialogmeldingFraBehandler.conversationRef}
                    msgType: ${kafkaDialogmeldingFraBehandler.msgType}
                """.trimIndent()
            )
        }
    }

    private fun findUtgaendeMelding(
        kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        conversationRef: UUID?,
        connection: Connection,
    ): PMelding? {
        val utgaaende = conversationRef?.let {
            connection.getUtgaendeMeldingerInConversation(
                uuidParam = conversationRef,
                arbeidstakerPersonIdent = PersonIdent(kafkaDialogmeldingFraBehandler.personIdentPasient),
            )
        } ?: mutableListOf()

        if (utgaaende.isEmpty() && !kafkaDialogmeldingFraBehandler.parentRef.isNullOrBlank()) {
            val parentRef = try {
                UUID.fromString(kafkaDialogmeldingFraBehandler.parentRef)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (parentRef != null) {
                utgaaende.addAll(
                    connection.getUtgaendeMeldingerInConversation(
                        uuidParam = parentRef,
                        arbeidstakerPersonIdent = PersonIdent(kafkaDialogmeldingFraBehandler.personIdentPasient),
                    )
                )
            }
        }
        return utgaaende.firstOrNull()
    }

    private fun handleHenvendelseTilNAV(
        connection: Connection,
        kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        conversationRef: UUID?,
    ) {
        val latestOppfolgingstilfelle = runBlocking {
            oppfolgingstilfelleClient.getOppfolgingstilfelle(
                personIdent = PersonIdent(kafkaDialogmeldingFraBehandler.personIdentPasient),
            )
        }
        if (latestOppfolgingstilfelle?.isActive() == true) {
            storeDialogmeldingFromBehandler(
                connection = connection,
                kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                type = MeldingType.HENVENDELSE_MELDING_TIL_NAV,
                conversationRef = conversationRef ?: UUID.randomUUID(),
            )
        } else {
            log.info("Received dialogmelding til NAV from behandler, but skipped since no active oppfolgingstilfelle")
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FOR_MODIA.increment()
        }
    }

    private fun storeDialogmeldingFromBehandler(
        connection: Connection,
        kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        type: MeldingType,
        conversationRef: UUID,
    ) {
        if (connection.getMeldingForMsgId(kafkaDialogmeldingFraBehandler.msgId) != null) {
            log.warn("Received a duplicate dialogmelding of type $type from behandler: $conversationRef")
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_DUPLICATE.increment()
        } else {
            log.info("Received a dialogmelding of type $type from behandler: $conversationRef")

            val meldingFraBehandler = kafkaDialogmeldingFraBehandler.toMeldingFraBehandler(
                type = type,
                conversationRef = conversationRef,
            )
            val meldingId = connection.createMeldingFraBehandler(
                meldingFraBehandler = meldingFraBehandler,
                fellesformat = kafkaDialogmeldingFraBehandler.fellesformatXML,
            )
            if (kafkaDialogmeldingFraBehandler.antallVedlegg > 0) {
                val vedlegg = mutableListOf<VedleggDTO>()
                runBlocking {
                    vedlegg.addAll(
                        padm2Client.hentVedlegg(kafkaDialogmeldingFraBehandler.msgId)
                    )
                }
                vedlegg.forEachIndexed { index, vedleggDTO ->
                    connection.createVedlegg(
                        pdf = vedleggDTO.bytes,
                        meldingId = meldingId,
                        number = index,
                        commit = false,
                    )
                }
            }
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_MELDING_CREATED.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaDialogmeldingFraBehandlerConsumer::class.java)
    }
}
