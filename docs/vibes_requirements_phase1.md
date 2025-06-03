------



我们的角色: 专注于项目中 kafka, springboot微服务 中间件开发的工程师!!!

这是一个虚拟项目, 目的是提高我的 Kafka 开发水平, 所以我们不需要去考虑用户授权或隐私方面的问题

对于不属于我们工作内容的部分, 比如推荐系统, 广告系统, 我们假定这些系统存在, 且开放了api端口

# VIBES 第一阶段：需求分析与 MVP 定义

------

## 一、产品愿景（Product Vision）

VIBES 是一个移动端优先的社交应用，类似于 **Threads**，但其核心差异点在于**聚焦用户的线下约会机会**。该平台基于 **Apache Kafka 构建的事件驱动架构**，后端使用 Spring Boot 框架。VIBES 成功的关键在于精准地为用户推荐内容与用户，尤其是通过用户在平台上的隐性行为来推断他们的约会期望，从而提升约会成功率。

------

## 二、目标用户（Target Audience）

- 希望与他人建立真实线下约会关系的用户。
- 愿意让自己的线上行为影响平台的内容推荐结果。

------

## 三、MVP 核心目标（Core Goals）

VIBES MVP 阶段的首要目标是验证以下核心用户互动模式：

1. **鼓励用户通过积极互动**（发帖、浏览、点赞等），主动管理自身在平台上的曝光度和吸引力。
2. **通过隐性行为建立用户的个人画像/模型**，而非依赖用户的显式兴趣设定。
3. 基于用户画像，构建精准的**推荐引擎**，向用户推荐相关内容和其他用户。
4. **促成用户匹配与对话**，通过行为驱动且画像为中心的模式实现。
5. 以 **Kafka 为技术基础**，确保系统具备高扩展性及事件驱动能力。

------

## 四、MVP 功能列表（Feature List）

### A. 用户身份及基础个人资料建立

- **A.1 用户注册**
  - 手机号或邮箱 + 密码方式
  - 注册或首次使用时获得用户地理位置授权（用户明确同意）
- **A.2 用户登录**
  - 已注册用户的安全登录机制
- **A.3 简化用户资料**
  - 昵称
  - 头像（初期单张，未来支持相册）
  - 性别
  - 年龄（通过生日计算）
  - 主要地区/城市（根据首次注册时地理位置预填，用户可修改确认）
  - 上传个人图片（最多5~6张）

### B. 核心行为及用户模型的培养

- **B.1 发布帖子**
  - 支持发布文字内容
  - 可附带一张或多张图片
  - 帖子自动记录发布时用户的地理位置
- **B.2 推荐内容消费**
  - 用户主要通过推荐流查看内容（详见E.1）
- **B.3 浏览内容与用户资料**
  - 支持浏览完整帖子详情
  - 支持浏览其他用户的个人资料
  - 浏览行为产生带有用户地理位置和浏览时长的事件
- **B.4 帖子点赞**
  - 用户可点赞帖子
  - 点赞行为产生带有点赞者地理位置的事件

### C. 核心匹配机制与用户模型验证

- **C.1 表达兴趣（对用户表示兴趣）**
  - 用户可在他人资料或帖子上直接表达兴趣（类似于“喜欢”按钮）
  - 行为产生事件，包含兴趣表达者地理位置
- **C.2 匹配机制**
  - 两个用户相互表达兴趣即形成“匹配”（Match）

### D. 匹配后的互动与激励机制

- **D.1 基础文本聊天**
  - 仅允许匹配后的用户之间实时一对一文本聊天
- **D.2 核心通知机制**
  - 当其他用户对自己表达兴趣或形成匹配时，用户将收到系统通知

### E. 后台与系统核心支撑模块

- **E.1 推荐流服务（Feed Service）**
  - **冷启动阶段**：新用户初次登录时，根据注册地理位置，展示区域热门内容与用户（数据来自 E.2 服务）
  - **正常运行阶段**：调用推荐引擎服务（E.3），展示个性化内容与用户
- **E.2 区域热门内容服务（Regional Hot Content Service，新建服务）**
  - 从 Kafka 订阅并消费包含地理信息的事件：`PostCreatedEvent`、`PostViewedEvent`、`PostLikedEvent`
  - 基于时间窗口和地理区域聚合事件，得出热门内容与用户
  - 产生区域热门内容更新事件 (`RegionalHotContentUpdatedEvent`)，供Feed服务冷启动使用
- **E.3 推荐引擎服务（Recommendation Service，外部或虚拟实现）**
  - 从 Kafka 订阅所有用户行为与画像事件
  - 提供推荐内容与用户的 API（如 `getRecommendedPosts`、`getRecommendedUsers`）供Feed服务调用
- **E.4 事件流基础设施（Kafka）**
  - 所有用户行为、系统事件均通过 Kafka 进行事件驱动处理

------

## 五、MVP阶段关键Kafka事件（多数包含地理位置）

| 事件名称                 | 事件字段                                                     |
| ------------------------ | ------------------------------------------------------------ |
| 用户注册                 | `userId, 时间戳, 注册方式, 地理位置`                         |
| 用户资料更新             | `userId, 更新字段, 时间戳`                                   |
| 用户照片上传             | `userId, 图片URL, 图片序列, 时间戳`                          |
| 帖子发布                 | `postId, userId, 文字内容, 图片URL列表, 时间戳, 地理位置`    |
| 帖子浏览                 | `viewerId, postId, authorId, 浏览时长, 时间戳, 浏览者地理位置` |
| 用户资料浏览             | `viewerId, viewedProfileUserId, 浏览时长, 时间戳, 浏览者地理位置` |
| 帖子点赞                 | `likerId, postId, authorId, 时间戳, 点赞者地理位置`          |
| 用户表达兴趣             | `declarerId, targetUserId, 时间戳, 表达兴趣者地理位置`       |
| 用户匹配成功             | `userId1, userId2, 匹配成功时间戳`                           |
| 聊天消息发送             | `messageId, chatId, senderId, receiverId, 消息内容, 时间戳`  |
| 区域热门内容更新（内部） | `区域标识, 热门帖子列表, 热门用户列表, 更新时间戳`           |

------

- UserRegisteredEvent(userId, timestamp, geoLocation)

- UserProfileUpdatedEvent(userId, updatedFields, timestamp)

- UserPhotoUploadedEvent(userId, photoUrl, timestamp)

- PostCreatedEvent(postId, userId, text, imageUrls, timestamp, geoLocation)

- PostViewedEvent(viewerId, postId, authorId, duration, timestamp, viewerGeoLocation)

- UserProfileViewedEvent(viewerId, viewedUserId, duration, timestamp, viewerGeoLocation)

- PostLikedEvent(likerId, postId, authorId, timestamp, likerGeoLocation)

- UserInterestDeclaredEvent(declarerId, interestedInUserId, timestamp, declarerGeoLocation)

- UserMatchCreatedEvent(userId1, userId2, timestamp)

- ChatMessageSentEvent(messageId, senderId, receiverId, content, timestamp)

- (Internal) RegionalHotContentUpdatedEvent(region, hotPostIds, hotUserIds, timestamp) (由 RegionalHotContentService 生产，被 FeedService 消费)

## 六、非功能性需求（高层次）

- **扩展性**：借助Kafka的高扩展性架构，预留未来用户与数据量扩容空间。
- **性能**：关键交互与推荐流需快速响应，事件处理需实时或近实时。
- **可靠性**：Kafka确保消息持久性，用户数据与帖子数据需可靠持久化存储（具体数据库待第三阶段确定）。
- **安全性**：基础认证与API安全访问控制（后续版本增强）。
- **隐私性**：严格考虑用户地理位置数据的授权与匿名聚合处理。

------

## 七、排除或延期的功能（后续迭代考虑）

- 关注关系详细管理与相关Feed流
- 帖子评论系统（除点赞外）
- 高级搜索功能
- 丰富通知类型（匹配/兴趣通知之外）
- 用户拉黑/举报（快速跟进MVP后实现）
- 删除帖子等内容功能
- 帖子编辑或资料修改（初期简化）
- 密码恢复（初期仅基础方案）
- 推送通知（初期仅App内通知）
- 支持复杂媒体类型（如视频）

本需求分析文档将作为后续 VIBES MVP 开发的基础参考文件，后续功能范围或调整应参照并更新该文档。



