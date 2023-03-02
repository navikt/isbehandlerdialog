package no.nav.syfo.melding.kafka

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.melding.kafka.domain.KafkaDialogmeldingFraBehandlerDTO
import no.nav.syfo.testhelper.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.*

class KafkaDialogmeldingFraBehandlerSpek : Spek({

    with(TestApplicationEngine()) {
        start()
        val database = ExternalMockEnvironment.instance.database

        afterEachTest {
            database.dropData()
        }

        describe("Read dialogmelding sent from behandler to NAV from Kafka Topic") {
            describe("Happy path") {
                it("Receive dialogmeldinger") {
                    val dialogmelding = generateDialogmeldingFraBehandlerDTO(UUID.randomUUID())
                    val mockConsumer = mockKafkaConsumerWithDialogmelding(dialogmelding)

                    runBlocking {
                        pollAndProcessDialogmeldingFraBehandler(
                            kafkaConsumerDialogmeldingFraBehandler = mockConsumer,
                            database = database,
                        )
                    }

                    verify(exactly = 1) { mockConsumer.commitSync() }
                }
            }
        }
    }
})

fun mockKafkaConsumerWithDialogmelding(dialogmelding: KafkaDialogmeldingFraBehandlerDTO): KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO> {
    val partition = 0
    val dialogmeldingTopicPartition = TopicPartition(
        DIALOGMELDING_FROM_BEHANDLER_TOPIC,
        partition,
    )

    val dialogmeldingRecord = ConsumerRecord(
        DIALOGMELDING_FROM_BEHANDLER_TOPIC,
        partition,
        1,
        dialogmelding.msgId,
        dialogmelding,
    )

    val mockConsumer = mockk<KafkaConsumer<String, KafkaDialogmeldingFraBehandlerDTO>>()
    every { mockConsumer.poll(any<Duration>()) } returns ConsumerRecords(
        mapOf(
            dialogmeldingTopicPartition to listOf(
                dialogmeldingRecord,
            )
        )
    )
    every { mockConsumer.commitSync() } returns Unit

    return mockConsumer
}
