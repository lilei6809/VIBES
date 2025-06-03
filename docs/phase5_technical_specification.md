# VIBES 项目 PHASE 5: 技术规格文档

## 引言

本文档详细记录了 VIBES 项目在 **PHASE 5: Technical Specification (技术规格)** 阶段的讨论成果与关键决策。它旨在为项目的具体实施提供清晰、详尽的技术指引，确保开发团队对各项规格有统一的理解。

## 1. 实现分解 (Implementation Breakdown)

项目将按以下阶段和模块进行实现，明确各模块的依赖关系：

*   **Phase 5.1: 核心基础设施与基础服务设置**
    *   **Modules:**
        *   **Parent POM / Project Structure:** 建立多模块 Maven 项目结构 (`vibes-parent`, `vibes-api-gateway`, `vibes-user-service`, `vibes-post-service`, `vibes-interaction-service`, `vibes-match-service`, `vibes-chat-service`, `vibes-regional-hot-content-service`, `vibes-feed-service`, `vibes-event-schemas` (或 `vibes-commons`)).
        *   **`vibes-event-schemas` (or `vibes-commons`):** 创建共享模块，存放 Protobuf 定义 (`.proto` 文件) 和生成的 Java 类。
        *   **Kafka & Schema Registry Setup (Local/Testcontainers):** 配置本地开发环境 (Docker Compose for Kafka & Schema Registry) 和集成测试环境 (Testcontainers)。
        *   **API Gateway (`vibes-api-gateway`):** 基础搭建，引入 Spring Cloud Gateway，配置分布式追踪基础。
    *   **Dependencies:** 所有其他服务的基础。

*   **Phase 5.2: 用户模块**
    *   **Module:** `vibes-user-service`
    *   **Key Features:** 用户注册 (API + 事件), 用户登录 (API), 查询用户资料 (API), 更新用户资料 (API + 事件), 上传照片 (API + 事件)。
    *   **Kafka Events Produced:** `UserRegisteredEvent`, `UserProfileUpdatedEvent`, `UserPhotoUploadedEvent`.
    *   **Dependencies:** Phase 5.1, `vibes-event-schemas`.

*   **Phase 5.3: 帖子模块**
    *   **Module:** `vibes-post-service`
    *   **Key Features:** 发布帖子 (API + 事件), 查询帖子详情 (API)。
    *   **Kafka Events Produced:** `PostCreatedEvent`. (后续可能补充 `PostViewedEvent` 的生产者逻辑，如果浏览也通过API触发)
    *   **Dependencies:** Phase 5.1, `vibes-event-schemas`.

*   **Phase 5.4: 互动模块**
    *   **Module:** `vibes-interaction-service`
    *   **Key Features:** 点赞帖子 (API + 事件), 对用户表达兴趣 (API + 事件)。
    *   **Kafka Events Produced:** `PostLikedEvent`, `UserInterestDeclaredEvent`.
    *   **Kafka Events Consumed (Potentially):** `PostViewedEvent` (如果浏览行为需要在此服务处理以产生下游影响或统计)。
    *   **Dependencies:** Phase 5.1, `vibes-event-schemas`.

*   **Phase 5.5: 匹配模块**
    *   **Module:** `vibes-match-service`
    *   **Key Features:** 消费 `UserInterestDeclaredEvent`, 检测双方匹配, 发布 `UserMatchCreatedEvent`.
    *   **Kafka Events Consumed:** `UserInterestDeclaredEvent`.
    *   **Kafka Events Produced:** `UserMatchCreatedEvent`.
    *   **Dependencies:** Phase 5.1, `vibes-event-schemas`.

*   **Phase 5.6: 区域热门内容模块**
    *   **Module:** `vibes-regional-hot-content-service`
    *   **Key Features:** 消费 `PostCreatedEvent`, `PostViewedEvent`, `PostLikedEvent`; 基于每日滚动时间窗口和动态半径聚合计算热门内容/用户; 发布 `RegionalHotContentUpdatedEvent`; 提供查询热门内容的 API。
    *   **Kafka Events Consumed:** `PostCreatedEvent`, `PostViewedEvent`, `PostLikedEvent`.
    *   **Kafka Events Produced:** `RegionalHotContentUpdatedEvent`.
    *   **Dependencies:** Phase 5.1, `vibes-event-schemas`.

*   **Phase 5.7: 推荐流模块**
    *   **Module:** `vibes-feed-service`
    *   **Key Features:** 提供内容推荐流 API 给客户端，冷启动时调用 `RegionalHotContentService`，正常运行时调用虚拟的 `RecommendationService`。
    *   **Dependencies:** Phase 5.1, `vibes-regional-hot-content-service`.

*   **Phase 5.8: 聊天模块**
    *   **Module:** `vibes-chat-service`
    *   **Key Features:** 允许匹配用户间发送和接收文本消息 (API/WebSocket + 事件)。
    *   **Kafka Events Produced:** `ChatMessageSentEvent`.
    *   **Dependencies:** Phase 5.1, `vibes-event-schemas`, `vibes-match-service` (隐式依赖于匹配关系的存在).

*   **Phase 5.9: 整体集成与测试**
    *   端到端测试核心用户流程。
    *   验证分布式追踪 (Correlation ID) 的有效性。
    *   完善文档和测试覆盖率。

## 2. 技术风险与缓解策略 (Technical Risks & Mitigation)

*   **风险 1: Protobuf 与 Schema Registry 集成复杂性**
    *   **描述:** 首次配置 Protobuf 序列化、Maven 插件及与 Confluent Schema Registry 的交互可能遇到问题。
    *   **缓解:** 尽早（Phase 5.1/5.2）进行原型验证；遵循官方文档；使用 Testcontainers 进行集成测试。

*   **风险 2: Kafka Topic 与分区策略不当**
    *   **描述:** 可能导致性能、消息顺序或扩展性问题。
    *   **缓解:** 仔细规划 Topic 用途；对需按序处理的事件使用一致性 Key 分区；初期分区数保守，后续按需调整。

*   **风险 3: 事件驱动的最终一致性理解与调试**
    *   **描述:** 异步事件处理的最终一致性及跨服务调试可能困难。
    *   **缓解:** 尽早引入分布式追踪 (Correlation ID)；实现幂等性消费者；充分日志记录；使用 Kafka 工具检查 Topic。

*   **风险 4: H2 数据库用于微服务的局限性 (MVP)**
    *   **描述:** H2 简化了 MVP 部署，但不能完全模拟分布式数据库特性。
    *   **缓解:** 明确 H2 为临时方案；业务逻辑避免依赖 H2 特有功能；通过 Testcontainers 测试；为未来迁移做准备 (JPA)。

*   **风险 5: 微服务配置管理**
    *   **描述:** 多服务带来多配置文件管理的复杂性。
    *   **缓解:** MVP 阶段使用 Spring Profiles；未来可考虑 Spring Cloud Config Server；本地开发注意端口管理。

## 3. 详细组件规格 (Detailed Component Specifications)

### 3.1 Kafka 事件契约 (Protobuf Schemas)

*   **共享模块:** `.proto` 文件及生成的 Java 类将存放在 `vibes-event-schemas` (或 `vibes-commons`) 模块中。
*   **Topic 命名约定:** `vibes.events.<entity>.<action>` (例: `vibes.events.user.registered`).
*   **Partitioning Key Strategy:** 通常使用事件关联的主要实体 ID (例: `userId`, `postId`) 作为 Key，以确保同一实体的事件进入同一分区，保证顺序性并有利于消费者扩展。
*   **`extra_data` 和 `event_type_enum`:** 明确决定在 MVP 阶段**不普遍引入**这两个元素，以保持 Schema 的简洁和强类型。
*   **`EventMetadata`:** 采纳选项 C，MVP 阶段不强制所有事件包含完整的 `EventMetadata`，核心业务字段和时间戳已足够。`correlation_id` 将通过日志追踪系统传递和记录。如果特定事件需要唯一 `event_id`，可作为普通字段加入。

**Protobuf 定义:**

**A. `vibes/common/common.proto`**
```protobuf
syntax = "proto3";

package vibes.common;

option java_package = "com.vibes.common.pb";
option java_multiple_files = true;

// 地理位置信息
message GeoLocation {
  double latitude = 1;  // 纬度
  double longitude = 2; // 经度
}
```

**B. `vibes/user/user_events.proto`**
```protobuf
syntax = "proto3";

package vibes.events.user;

import "vibes/common/common.proto"; // 导入通用类型

option java_package = "com.vibes.events.user.pb";
option java_multiple_files = true;

// --- 用户注册事件 ---
// Topic: vibes.events.user.registered
// Key: userId
message UserRegisteredEvent {
  string user_id = 1;
  int64 registration_timestamp = 2; // UTC milliseconds
  string registration_method = 3;   // e.g., "EMAIL", "PHONE"
  vibes.common.GeoLocation registration_location = 4;
  string nickname = 5;
  optional string email = 6;
  optional string phone_number = 7;
  optional string gender = 8;
  optional string birth_date = 9; // YYYY-MM-DD
}

// --- 用户资料更新事件 ---
// Topic: vibes.events.user.profile_updated
// Key: userId
message UserProfileUpdatedEvent {
  string user_id = 1;
  int64 update_timestamp = 2;
  optional string nickname = 3;
  optional string gender = 4;
  optional string birth_date = 5; // YYYY-MM-DD
  optional string city = 6;
}

// --- 用户照片上传事件 ---
// Topic: vibes.events.user.photo_uploaded
// Key: userId
message UserPhotoUploadedEvent {
  string user_id = 1;
  string photo_id = 2; // 照片的唯一ID
  string photo_url = 3;
  int32 sequence = 4; // 照片顺序 (例如1是主头像)
  int64 upload_timestamp = 5;
}
```

**C. `vibes/post/post_events.proto`**
```protobuf
syntax = "proto3";

package vibes.events.post;

import "vibes/common/common.proto";

option java_package = "com.vibes.events.post.pb";
option java_multiple_files = true;

// --- 帖子发布事件 ---
// Topic: vibes.events.post.created
// Key: postId
message PostCreatedEvent {
  string post_id = 1;
  string author_id = 2;
  string text_content = 3;
  repeated string image_urls = 4;
  vibes.common.GeoLocation location = 5;
  int64 creation_timestamp = 6;
}

// --- 帖子浏览事件 ---
// Topic: vibes.events.post.viewed
// Key: postId (或 viewerId)
message PostViewedEvent {
  string post_id = 1;
  string viewer_id = 2; // 浏览者ID
  string author_id = 3; // 帖子作者ID
  int64 view_timestamp = 4;
  int32 view_duration_seconds = 5; // 浏览时长（秒）
  vibes.common.GeoLocation viewer_location = 6; // 浏览者当前位置
}
```

**D. `vibes/interaction/interaction_events.proto`**
```protobuf
syntax = "proto3";

package vibes.events.interaction;

import "vibes/common/common.proto";

option java_package = "com.vibes.events.interaction.pb";
option java_multiple_files = true;

// --- 帖子点赞事件 ---
// Topic: vibes.events.interaction.post_liked
// Key: postId (或 likerId)
message PostLikedEvent {
  string post_id = 1;
  string liker_id = 2;
  string author_id = 3; // 帖子作者ID
  int64 like_timestamp = 4;
  vibes.common.GeoLocation liker_location = 5;
}

// --- 用户表达兴趣事件 ---
// Topic: vibes.events.interaction.interest_declared
// Key: targetUserId (或 declarerId)
message UserInterestDeclaredEvent {
  string declarer_id = 1;     // 表达兴趣的人
  string target_user_id = 2;  // 被表达兴趣的人
  int64 declaration_timestamp = 3;
  vibes.common.GeoLocation declarer_location = 4;
  optional string source_post_id = 5; // 如果兴趣是通过某个帖子表达的
}
```

**E. `vibes/match/match_events.proto`**
```protobuf
syntax = "proto3";

package vibes.events.match;

option java_package = "com.vibes.events.match.pb";
option java_multiple_files = true;

// --- 用户匹配成功事件 ---
// Topic: vibes.events.match.created
// Key: (user_id1 + user_id2) sorted lexicographically to create a unique pair key, or a generated match_id
message UserMatchCreatedEvent {
  string match_id = 1; // 匹配的唯一ID
  string user_id1 = 2;
  string user_id2 = 3;
  int64 match_timestamp = 4;
  optional string interest_event_id1 = 5; // 关联的兴趣事件ID (可选)
  optional string interest_event_id2 = 6; // 关联的兴趣事件ID (可选)
}
```

**F. `vibes/chat/chat_events.proto`**
```protobuf
syntax = "proto3";

package vibes.events.chat;

option java_package = "com.vibes.events.chat.pb";
option java_multiple_files = true;

// --- 聊天消息发送事件 ---
// Topic: vibes.events.chat.message_sent
// Key: chat_id (or match_id)
message ChatMessageSentEvent {
  string message_id = 1;
  string chat_id = 2; // 聊天会话ID (通常等于 match_id)
  string sender_id = 3;
  string receiver_id = 4;
  string content_type = 5; // e.g., "TEXT"
  string content = 6;
  int64 sent_timestamp = 7;
}
```

**G. `vibes/regional_content/regional_content_events.proto`**
```protobuf
syntax = "proto3";

package vibes.events.regional_content;

// import "vibes/common/common.proto"; // GeoLocation not directly used here, but could be for region definition

option java_package = "com.vibes.events.regional_content.pb";
option java_multiple_files = true;

message HotPostInfo {
  string post_id = 1;
  double score = 2; // 热度分
}

message HotUserInfo {
  string user_id = 1;
  double score = 2; // 热度分
}

// --- 区域热门内容更新事件 ---
// Topic: vibes.events.regional_content.updated
// Key: region_id
message RegionalHotContentUpdatedEvent {
  string region_id = 1; // 区域标识 (e.g., "city_123", "geohash_abc", "radius_lat_lon_km")
  repeated HotPostInfo hot_posts = 2;
  repeated HotUserInfo hot_users = 3;
  int64 update_timestamp = 4;
  string time_window_description = 5; // e.g., "DAILY_ROLLING_24H"
}
```

### 3.2 RESTful API 契约 (概述)

详细的请求/响应体将在具体模块开发时定义。

*   **通用约定:**
    *   **Base URL Prefix:** (通过 API Gateway) `/v1` (e.g., `http://localhost:8080/v1`)
    *   **Content-Type:** `application/json`
    *   **Authentication (MVP):** 简化或无。用户身份信息（如 `userId`）可能通过请求参数、路径变量或自定义 Header 传递（不推荐后者）。
    *   **Error Response (Example):**
        ```json
        {
          "timestamp": "YYYY-MM-DDTHH:mm:ss.sssZ",
          "status": 400,
          "error": "Bad Request",
          "message": "Validation error: nickname must not be empty.",
          "path": "/v1/users/register"
        }
        ```

*   **UserService (`/users`)**
    *   `POST /register`: 用户注册
    *   `POST /login`: 用户登录 (返回 JWT 或 Session Token - MVP 简化)
    *   `GET /{userId}`: 获取用户公开资料
    *   `PUT /{userId}`: 更新用户资料 (需认证/授权)
    *   `POST /{userId}/photos`: 上传用户照片 (需认证/授权)

*   **PostService (`/posts`)**
    *   `POST /`: 创建新帖子 (需认证/授权)
    *   `GET /{postId}`: 获取帖子详情

*   **InteractionService (`/interactions`)**
    *   `POST /posts/{postId}/like`: 点赞/取消点赞帖子 (需认证/授权)
    *   `POST /users/{targetUserId}/interest`: 对用户表达兴趣 (需认证/授权)

*   **MatchService (`/matches`)**
    *   `GET /users/{userId}`: 获取某用户的匹配列表 (需认证/授权)

*   **ChatService (`/chats`)**
    *   `POST /messages`: 发送聊天消息 (需认证/授权, 请求体包含 `chatId/matchId`, `content`)
    *   `GET /{chatId}/messages`: 获取某聊天的历史消息 (需认证/授权, 支持分页)
    *   (WebSocket 端点用于实时消息: `ws://localhost:8080/ws/chat/{userId}` - 待细化)

*   **RegionalHotContentService (`/content/regional`)**
    *   `GET /hot`: 获取区域热门内容 (可带参数: `latitude`, `longitude`, `radiusInKm`, `limit`)

*   **FeedService (`/feed`)**
    *   `GET /`: 获取当前用户的推荐 Feed 流 (需认证/授权, 支持分页参数 `page`, `size`)

### 3.3 数据格式 (Data Formats)

*   **Kafka Events:** Protocol Buffers (Protobuf) as defined above.
*   **RESTful APIs:** JSON.
*   **API DTOs vs Protobuf Objects:** 倾向于为 API 设计专门的 POJO DTOs，并在服务边界进行与内部 Protobuf 对象的转换，以保持 API 契约的稳定性和关注点分离。

### 3.4 状态管理 (State Management)

*   每个微服务通过其专用的 H2 数据库实例 (MVP) 持久化和管理自身状态。
*   服务设计尽可能无状态，将状态委托给数据库。
*   `RegionalHotContentService` 的聚合逻辑在 MVP 阶段通过 Spring Kafka 消费者和业务逻辑实现，结果可缓存于 H2。

### 3.5 验证规则 (Validation Rules)

*   **微服务 API 入口:** 使用 Bean Validation (JSR 380/303 - Hibernate Validator) 对 API 请求 DTOs 进行声明式验证 (`@NotNull`, `@Email`, `@Size`, etc.)。验证失败返回统一的 `400 Bad Request` 错误。
*   **业务逻辑层:** 执行更复杂的业务规则校验。
*   **Protobuf 生产者端:** 确保发布的 Protobuf 消息字段符合业务预期。

## 4. 技术成功标准 (Technical Success Criteria for MVP)

1.  **核心事件流程通畅:** 所有关键用户行为能触发相应 Protobuf 事件发布到 Kafka，并被下游正确消费；Schema Registry 正常工作。
2.  **核心 API 功能可用:** API Gateway 正确路由；各微服务主要 API 按规格工作。
3.  **数据持久化与一致性 (MVP):**核心数据能正确持久化到 H2；事件驱动下系统状态在合理时间内达到一致。
4.  **服务可运行与可观测性基础:** 所有微服务能构建和运行；Testcontainers 用于集成测试 Kafka 和数据库；实现 Correlation ID 的跨服务日志追踪；Actuator 健康检查可用。
5.  **代码质量与可维护性基础:** Maven 多模块结构；Protobuf 定义清晰；关键业务逻辑有单元测试；代码风格基本一致。
6.  **`RegionalHotContentService` 核心逻辑实现:** 能消费事件，基于定义的时间窗口和半径逻辑产出热门内容更新事件，其 API 能为 `FeedService` 提供冷启动数据。

## 下一步行动

本文档详细规划了VIBES项目的技术规格。下一阶段将是 **PHASE 6: Transition Decision (过渡决策)**，届时将总结架构建议、实施路线图，并准备最终的 `SOW.md` 工作说明书。 