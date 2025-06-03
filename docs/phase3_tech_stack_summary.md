# VIBES 项目 PHASE 3: 技术选型总结

## 引言

本文档总结了 VIBES 项目在 **PHASE 3: Tech Stack (技术选型)** 阶段确定的技术、框架、库及其版本。这些选型将指导项目的开发环境搭建和具体实现。

## 一、核心开发平台与工具

*   **编程语言 & JDK:**
    *   Java 17 LTS
*   **构建工具:**
    *   Apache Maven
*   **核心框架:**
    *   Spring Boot `3.2.5`

## 二、事件流、序列化与 Schema 管理

*   **事件流平台:**
    *   Apache Kafka (客户端版本 `org.apache.kafka:kafka-clients:3.4.1`)
    *   (假设运行的 Kafka Broker 版本与 Confluent Platform 7.4.1 兼容, e.g., Kafka `3.4.x` 或更高)
*   **Schema 管理 & Confluent Platform:**
    *   Confluent Platform `7.4.1` (用于 Schema Registry 及相关组件)
    *   依赖包括: `io.confluent:kafka-protobuf-serializer`, `io.confluent:kafka-schema-registry-client` (版本 `7.4.1`)
*   **事件序列化格式:**
    *   Protocol Buffers (Protobuf)
    *   `com.google.protobuf:protobuf-java`: `3.25.3`
*   **Maven 插件 (for Protobuf):**
    *   `com.github.ascopes:protobuf-maven-plugin`: `3.2.0`
    *   `org.codehaus.mojo:build-helper-maven-plugin`: `3.6.0` (用于添加生成的源文件到编译路径)

## 三、微服务架构

*   **Spring Cloud 版本:**
    *   `2023.0.x` (例如 `2023.0.1`, 由 Spring Boot `3.2.5` BOM 管理)
*   **API Gateway:**
    *   Spring Cloud Gateway (使用 `spring-cloud-starter-gateway`)
    *   **路由方式 (MVP):** 在 API Gateway 配置中直接硬编码下游微服务的 URL。
*   **服务间通信:**
    *   主要通过 Apache Kafka 事件驱动。
    *   同步调用通过 API Gateway 路由的 RESTful API。

## 四、数据持久化 (MVP)

*   **数据库引擎:**
    *   H2 Database Engine
    *   **版本:** `2.2.224` (或最新稳定版，以 POM 文件为准)
    *   使用 `spring-boot-starter-data-jpa` 进行集成。

## 五、测试

*   **单元/集成测试框架:**
    *   JUnit 5 (Jupiter)
        *   `org.junit.jupiter:junit-jupiter-api`: `5.10.2`
        *   `org.junit.jupiter:junit-jupiter-engine`: `5.10.2`
*   **容器化测试支持:**
    *   Testcontainers
        *   `org.testcontainers:testcontainers`: `1.19.8`
        *   `org.testcontainers:kafka`: `1.19.8` (用于集成测试 Kafka)
        *   `org.testcontainers:junit-jupiter`: `1.19.8` (Testcontainers 与 JUnit 5 集成)
        *   (未来可能添加 `org.testcontainers:postgresql`, `org.testcontainers:mongodb` 等)

## 六、日志

*   **日志门面:**
    *   SLF4J API (`org.slf4j:slf4j-api`): `2.0.12`
*   **日志实现:**
    *   Logback Classic (由 Spring Boot `spring-boot-starter-logging` 默认管理和配置)

## 七、核心 Spring Boot Starters & 依赖

*   `spring-boot-starter-web` (用于构建 RESTful 服务)
*   `spring-boot-starter-data-jpa` (用于 JPA 数据持久化)
*   `spring-kafka` (用于 Kafka 集成)
*   `spring-boot-starter-actuator` (用于应用监控和管理)
*   `spring-boot-starter-test` (基础测试依赖)
*   `spring-cloud-starter-gateway` (API Gateway 服务)
*   Confluent Protobuf Serializer/Deserializer (`io.confluent:kafka-protobuf-serializer`, etc. version `7.4.1`)

## 八、辅助工具库

*   **Lombok:**
    *   `org.projectlombok:lombok` (最新版, `provided` scope，用于减少样板代码)

## 九、Maven Properties (参考)

以下是部分关键版本在 `pom.xml` 中 `<properties>` 段的参考 (完整列表以实际项目 POM 为准):

```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <spring-boot.version>3.2.5</spring-boot.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version> <!-- 请根据Spring Boot版本BOM确认 -->
    <testcontainers.version>1.19.8</testcontainers.version>
    <confluent.platform.version>7.4.1</confluent.platform.version> <!-- 用于Confluent依赖 -->
    <kafka.clients.version>3.4.1</kafka.clients.version> <!-- Apache Kafka客户端 -->
    <junit.jupiter.version>5.10.2</junit.jupiter.version>
    <slf4j.version>2.0.12</slf4j.version>
    <h2.version>2.2.224</h2.version>

    <!-- Protobuf versions -->
    <protobuf.version>3.25.3</protobuf.version>
    <ascopes.protobuf-maven-plugin.version>3.2.0</ascopes.protobuf-maven-plugin.version>
    <build-helper-maven-plugin.version>3.6.0</build-helper-maven-plugin.version>
    <lombok.version>1.18.30</lombok.version> <!-- 示例Lombok版本, 以最新为准 -->
</properties>
```

## 下一步

技术选型确定后，下一步将进入 **PHASE 4: Architecture Design (架构设计)**。 