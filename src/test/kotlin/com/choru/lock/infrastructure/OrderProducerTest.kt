package com.choru.lock.infrastructure

import com.choru.lock.dto.OrderCreateRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["order-create"]
)
class OrderProducerTest @Autowired constructor(
    private val orderProducer: OrderProducer,
    private val embeddedKafkaBroker: EmbeddedKafkaBroker,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { "\${spring.embedded.kafka.brokers}" }
        }
    }

    @Test
    fun `주문_요청을_보내면_Kafka_토픽에_저장되어야_한다`() {
        // given
        val request = OrderCreateRequest(userId = 1L, productId = 100L, couponId = 50L)
        // when
        orderProducer.sendCreateOrder(request)
        // then

        val consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java

        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProps)
        val consumer = consumerFactory.createConsumer()

        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "order-create")

        // 5초 동안 메시지가 오는지 기다림
        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))

        // 검증
        assertEquals(1, records.count(), "메시지가 1개 도착해야 함")

        val message = records.iterator().next().value()
        val receivedRequest = objectMapper.readValue(message, OrderCreateRequest::class.java)

        assertEquals(request.userId, receivedRequest.userId)
        assertEquals(request.productId, receivedRequest.productId)

        println(">>> 테스트 성공: Kafka 메시지 수신 확인 ($message)")

        consumer.close()
    }
}