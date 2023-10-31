package no.nav.syfo.melding.kafka.dialogmelding

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.*
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.client.oppfolgingstilfelle.isActive
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.client.padm2.VedleggDTO
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.kafka.domain.*
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.UUID

class KafkaDialogmeldingFraBehandlerConsumer(
    private val database: DatabaseInterface,
    private val padm2Client: Padm2Client,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val storeMeldingTilNAV: Boolean,
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
                    handleDialogmeldingFromBehandler(
                        kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                        connection = connection,
                    )
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
        if (kafkaDialogmeldingFraBehandler.dialogmelding.innkallingMoterespons != null) {
            return // dialogmøterelaterte meldinger konsumeres av isdialogmote
        }
        val utgaaendeMelding = findUtgaaendeMelding(
            kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
            connection = connection,
        )
        val conversationRef = kafkaDialogmeldingFraBehandler.conversationRef?.let { UUID.fromString(it) }
        if (utgaaendeMelding != null) {
            storeDialogmeldingFromBehandler(
                connection = connection,
                kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                type = MeldingType.valueOf(utgaaendeMelding.type),
                conversationRef = utgaaendeMelding.conversationRef,
            )
        } else if (kafkaDialogmeldingFraBehandler.isHenvendelseTilNAV()) {
            handleHenvendelseTilNAV(
                connection = connection,
                kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                conversationRef = conversationRef,
            )
        } else {
            log.info(
                """
                    Received dialogmelding from behandler, but no existing conversation
                    msgId: ${kafkaDialogmeldingFraBehandler.msgId}
                    conversationRef: ${kafkaDialogmeldingFraBehandler.conversationRef}
                    msgType: ${kafkaDialogmeldingFraBehandler.msgType}
                """.trimIndent()
            )
        }
    }

    private fun findUtgaaendeMelding(
        kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        connection: Connection,
    ): PMelding? {
        val utgaaende = kafkaDialogmeldingFraBehandler.conversationRef?.let {
            connection.getUtgaendeMeldingerInConversation(
                uuidParam = UUID.fromString(it),
                arbeidstakerPersonIdent = PersonIdent(kafkaDialogmeldingFraBehandler.personIdentPasient),
            )
        } ?: mutableListOf()

        if (utgaaende.isEmpty() && kafkaDialogmeldingFraBehandler.parentRef != null) {
            val parentRef = UUID.fromString(kafkaDialogmeldingFraBehandler.parentRef)
            utgaaende.addAll(
                connection.getUtgaendeMeldingerInConversation(
                    uuidParam = parentRef,
                    arbeidstakerPersonIdent = PersonIdent(kafkaDialogmeldingFraBehandler.personIdentPasient),
                )
            )
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
        if (storeMeldingTilNAV && latestOppfolgingstilfelle?.isActive() == true) {
            storeDialogmeldingFromBehandler(
                connection = connection,
                kafkaDialogmeldingFraBehandler = kafkaDialogmeldingFraBehandler,
                type = MeldingType.HENVENDELSE_MELDING_TIL_NAV,
                conversationRef = conversationRef ?: UUID.randomUUID(),
            )
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NO_CONVERSATION.increment()
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
