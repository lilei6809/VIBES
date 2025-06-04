package com.vibes; // 使用您的项目包名或一个合适的测试包名

import com.vibes.common.pb.GeoLocation;
import com.vibes.events.user.pb.UserRegisteredEvent;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers // 启用 Testcontainers 的 JUnit 5 扩展
public class KafkaContainerTest {


    protected static final Logger logger = LoggerFactory.getLogger(KafkaContainerTest.class);
    // 定义 Kafka 容器
    // 使用 Confluent Platform 的 Kafka 镜像，版本与 docker-compose.yml 中一致或兼容
    // 注意：Testcontainers 的 KafkaContainer 默认使用 Apache Kafka 镜像。
    // 要使用 Confluent Platform 镜像，我们需要确保其配置方式与 KafkaContainer 兼容，
    // 或者直接使用 GenericContainer 并进行更手动的配置。
    // 为了简单起见，我们先用 KafkaContainer 默认支持的 Kafka 镜像 (通常是 wurstmeister/kafka 或 bitnami/kafka)。
    // 如果需要严格匹配 cp-kafka 的 KRaft 模式，可能需要更复杂的 GenericContainer 设置。
    // 对于初级测试，一个标准的 Kafka 容器通常足够。

    // 使用标准的 Kafka 镜像 (例如 Bitnami Kafka，与Testcontainers Kafka模块兼容)
    // 如果您的 pom.xml 中的 confluent.platform.version 属性也想用于此处，需要一种方式传递它。
    // 为简单起见，这里硬编码一个兼容的 Docker 镜像名。
    private static final String KAFKA_IMAGE_NAME = "confluentinc/cp-kafka:7.4.1";

    // 网络配置
    protected static Network network = Network.newNetwork();

    protected static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME))
            .withNetwork(network)
            .withNetworkAliases("kafka-test")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    protected static GenericContainer<?> schemaRegistry;

    static {
        logger.info("Starting Kafka container from KafkaContainerTest...");

        kafka.start();

        logger.info("Kafka container started. Bootstrap servers: {}", kafka.getBootstrapServers());

        // Start Schema Registry in the static block as well
        logger.info("Configuring and starting Schema Registry container from KafkaContainerTest static block...");
        Slf4jLogConsumer schemaRegistryLogConsumer = new Slf4jLogConsumer(LoggerFactory.getLogger("SchemaRegistryContainerBase"));
        schemaRegistry = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.4.1"))
                .withExposedPorts(8081)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka-test:9092")
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withNetwork(network)
                .dependsOn(kafka)
                .waitingFor(Wait.forHttp("/subjects")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(1)))
                .withLogConsumer(schemaRegistryLogConsumer);

        try {
            schemaRegistry.start();
            logger.info("Schema Registry container started in static block. URL: http://{}:{}", schemaRegistry.getHost(), schemaRegistry.getFirstMappedPort());
        } catch (Exception e) {
            logger.error("Failed to start Schema Registry container in static block.", e);
            throw new RuntimeException("Failed to start Schema Registry for tests", e);
        }
    }



    // @Container 注解会自动管理容器的生命周期 (启动和停止)
    // 对于 Kafka KRaft模式，直接用 KafkaContainer 可能不支持，它通常用于 Zookeeper模式的Kafka。
    // 我们这里先用一个标准的 KafkaContainer，如果需要 KRaft，则需要自定义 GenericContainer。
    // 为了让这个初始测试简单，我们用一个默认的 KafkaContainer (它会启动一个带Zookeeper的Kafka)。
    // 这是一个简化，目的是先验证 Testcontainers 的基本工作流程。
//    @Container
//    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.1"))
//            .withEmbeddedZookeeper(); // KafkaContainer 默认需要 Zookeeper，除非配置为 KRaft
    // Confluent 的 cp-kafka 镜像在高版本中是支持 KRaft 的，但 KafkaContainer 模块本身可能需要特定配置
    // .withKraft() 是较新 Testcontainers Kafka 模块的功能，可以尝试
    // 但如果用 cp-kafka 镜像，它可能不直接识别 .withKraft()
    // 最简单的方式是让 KafkaContainer 启动它自带的 Zookeeper

    /*
    // 如果要尝试 KRaft (实验性，且需要 Testcontainers Kafka 模块支持及正确配置的镜像):
    @Container
    public static KafkaContainer kafkaKRaft = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME))
        .withEnv("KAFKA_NODE_ID", "1")
        .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
        .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093") // 注意：localhost 在容器内部可能指向容器自己
        .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093")
        .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://" + kafkaKRaft.getHost() + ":" + kafkaKRaft.getMappedPort(9092))
        .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
        .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
        .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
        .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk") // 替换为有效的Base64 UUID
        .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
        .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
        .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
        .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
        .withNetworkAliases("kafka-kraft-test") // 便于其他容器连接 (如果需要)
        .withKraft(); // 尝试启用KRaft模式，这需要Testcontainers版本支持
    */


    @Test
    void kafkaContainerShouldBeRunning() throws ExecutionException, InterruptedException {
        assertThat(kafka.isRunning()).isTrue();

        // 创建一个 AdminClient 来与 Kafka 容器交互
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(props)) {
            // 创建一个测试 Topic
            String topicName = "testcontainers-topic-" + System.currentTimeMillis();
            NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();

            // 验证 Topic 是否被创建
            boolean topicExists = adminClient.listTopics().names().get().contains(topicName);
            assertThat(topicExists).isTrue();

            System.out.println("Kafka Bootstrap Servers: " + kafka.getBootstrapServers());
            System.out.println("Test topic '" + topicName + "' created successfully.");
        }
    }

    // 在 KafkaContainerTest 类中添加：

    // Helper method to get Schema Registry URL
    private String getSchemaRegistryUrl() {
        // 确保 schemaRegistry 容器已经启动并运行
        if (schemaRegistry == null || !schemaRegistry.isRunning()) {
            // 在 static 块中已经启动，但以防万一，或如果改为非 static 启动
            logger.info("Schema Registry not running, attempting to start for test...");
            try {
                schemaRegistry.start(); // 尝试启动，如果它不是由 @Container 管理的
            } catch (Exception e) {
                throw new IllegalStateException("Schema Registry container could not be started for test", e);
            }
        }
        return String.format("http://%s:%d", schemaRegistry.getHost(), schemaRegistry.getMappedPort(8081));
    }

    @Test
    void shouldSerializeAndDeserializeWithSchemaRegistry() {
        assertThat(kafka.isRunning()).isTrue();
        assertThat(schemaRegistry.isRunning()).isTrue(); // 确保 Schema Registry 也运行

        String topicName = "proto-events-topic-" + System.currentTimeMillis();
        String bootstrapServers = kafka.getBootstrapServers();
        String schemaRegistryUrl = getSchemaRegistryUrl();

        logger.info("Kafka for SR test: {}", bootstrapServers);
        logger.info("Schema Registry for SR test: {}", schemaRegistryUrl);

        // 1. 配置生产者
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class.getName()); // Confluent Protobuf Serializer
        producerProps.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        producerProps.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true); // 允许自动注册 Schema

        try (KafkaProducer<String, UserRegisteredEvent> producer = new KafkaProducer<>(producerProps)) {
            // 2. 创建 Protobuf 消息
            UserRegisteredEvent originalEvent = UserRegisteredEvent.newBuilder()
                    .setUserId("test-user-123")
                    .setNickname("TestUser")
                    .setRegistrationTimestamp(System.currentTimeMillis())
                    .setRegistrationMethod("TEST")
                    .setRegistrationLocation(
                            GeoLocation.newBuilder().setLatitude(10.0).setLongitude(20.0).build())
                    .build();

            // 3. 发送消息
            producer.send(new ProducerRecord<>(topicName, originalEvent.getUserId(), originalEvent)).get(); // .get() for synchronous send in test
            logger.info("Sent Protobuf message: {}", originalEvent);

        } catch (Exception e) {
            Assertions.fail("Error sending Protobuf message", e);
        }

        // 4. 配置消费者
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-sr-consumer-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class.getName()); // Confluent Protobuf Deserializer
        consumerProps.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // 指定要反序列化的具体 Protobuf 类型
        consumerProps.put("specific.protobuf.value.type", UserRegisteredEvent.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        try (KafkaConsumer<String, UserRegisteredEvent> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topicName));
            ConsumerRecords<String, UserRegisteredEvent> records = consumer.poll(Duration.ofSeconds(10)); // Poll for records

            assertThat(records.count()).isEqualTo(1);
            UserRegisteredEvent deserializedEvent = records.iterator().next().value();
            logger.info("Received Protobuf message: {}", deserializedEvent);

            // 5. 断言
            assertThat(deserializedEvent).isNotNull();
            assertThat(deserializedEvent.getUserId()).isEqualTo("test-user-123");
            assertThat(deserializedEvent.getNickname()).isEqualTo("TestUser");
            assertThat(deserializedEvent.getRegistrationLocation().getLatitude()).isEqualTo(10.0);

        } catch (Exception e) {
            Assertions.fail("Error receiving or deserializing Protobuf message", e);
        }
    }
}
