package no.nav.syfo.testhelper.mock

import io.mockk.every
import io.mockk.mockk
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import java.time.Duration

fun <ConsumerRecordValue> mockKafkaConsumer(
    consumerRecordValue: ConsumerRecordValue,
    topic: String,
): KafkaConsumer<String, ConsumerRecordValue> {
    val topicPartition = TopicPartition(
        topic,
        0
    )
    val consumerRecord = ConsumerRecord(
        topic,
        0,
        1,
        "key",
        consumerRecordValue
    )

    val mockConsumer = mockk<KafkaConsumer<String, ConsumerRecordValue>>()
    every { mockConsumer.poll(any<Duration>()) } returns ConsumerRecords(
        mapOf(
            topicPartition to listOf(
                consumerRecord,
            )
        )
    )
    every { mockConsumer.commitSync() } returns Unit

    return mockConsumer
}
