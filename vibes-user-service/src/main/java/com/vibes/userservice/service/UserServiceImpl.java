package com.vibes.userservice.service;

import com.google.protobuf.Message;
import com.vibes.events.user.pb.UserRegisteredEvent;
import com.vibes.common.pb.GeoLocation; // Protobuf GeoLocation class
import com.vibes.userservice.dto.UserRegistrationRequestDto;
import com.vibes.userservice.dto.UserResponseDTO; // Import UserResponseDTO
import com.vibes.userservice.exception.UserAlreadyExistsException;
import com.vibes.userservice.model.OutboxEvent;
import com.vibes.userservice.model.User;
import com.vibes.userservice.model.UserPhoto; // For future use if populating avatarUrl from photos list
import com.vibes.userservice.repository.OutboxEventRepository;
import com.vibes.userservice.repository.UserRepository;
import com.vibes.userservice.mapper.UserMapper; // Import the new mapper
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map; // Add this import
// import java.util.Comparator; // For sorting photos if needed

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository; // Injected repository
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate; // Re-injecting for the old method
    private final UserMapper userMapper; // Inject the mapper

    @Value("${vibes.kafka.topics.user-registered}")
    private String userRegisteredTopic;

    // We still need the schema.registry.url from application properties
    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    /**
     * outbox pattern control transaction
     * @param request DTO containing user registration details.
     * @return
     */
    @Override
    @Transactional
    public UserResponseDTO registerUser(UserRegistrationRequestDto request) {
        log.info("Registering user with outbox pattern for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + request.getEmail() + " already exists.");
        }

        User user = new User();
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());

        /**
         * 对 password 进行 hash 处理
         */
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        if (request.getGender() != null) {
            user.setGender(request.getGender().toUpperCase());
        }
        if (request.getBirthDate() != null && !request.getBirthDate().isEmpty()) {
            try {
                user.setBirthDate(LocalDate.parse(request.getBirthDate(), DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (Exception e) {
                log.warn("Invalid birth date format: {}. Skipping birth date.", request.getBirthDate());
                // Optionally throw InvalidInputException or handle as per requirements
            }
        }
        if (request.getRegistrationLatitude() != null && request.getRegistrationLongitude() != null) {
            user.setRegistrationLatitude(request.getRegistrationLatitude());
            user.setRegistrationLongitude(request.getRegistrationLongitude());
        }


        /**
         * 保存到数据库
         */
        User savedUser = userRepository.saveAndFlush(user);
        log.info("User saved to database with ID: {}", savedUser.getId());

        /**
         * OutBox
         */
        // --- Outbox Pattern Logic ---
        // 1. Build the Protobuf event message
        UserRegisteredEvent registeredEvent = buildUserRegisteredEvent(savedUser);
        
        // 2. Serialize the event payload to bytes using the Kafka Protobuf serializer
        byte[] payload = serializePayload(registeredEvent);

        // 3. Create and save the OutboxEvent entity
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(savedUser.getId())
                .eventType(UserRegisteredEvent.class.getSimpleName())
                .payload(payload)
                .destinationTopic(userRegisteredTopic)
                .build();

        // 保存 outboxEvent
        outboxEventRepository.save(outboxEvent);
        log.info("Outbox event saved for user ID: {}", savedUser.getId());
        // --- End of Outbox Pattern Logic ---

        // The business transaction ends here. User and OutboxEvent are saved atomically.
        // The actual Kafka message sending is handled by a separate poller.

        // Use the mapper to convert the entity to a DTO
        return userMapper.userToUserResponseDTO(savedUser);
    }

    @Transactional
    public UserResponseDTO registerUser_Simple(UserRegistrationRequestDto request) {
        log.info("Registering user with simple pattern for email: {}", request.getEmail());
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + request.getEmail() + " already exists.");
        }
        User user = new User();
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        // ... (set other properties on user)
        // save to database
        User savedUser = userRepository.saveAndFlush(user);
        log.info("User saved to database with ID: {}", savedUser.getId());
        UserRegisteredEvent registeredEvent = buildUserRegisteredEvent(savedUser);
        try {
            // send to kafka broker
            kafkaTemplate.send(userRegisteredTopic, savedUser.getId(), registeredEvent);
            log.info("UserRegisteredEvent sent to Kafka topic {}: User ID {}", userRegisteredTopic, savedUser.getId());
        } catch (Exception e) {
            log.error("Failed to send UserRegisteredEvent to Kafka for user ID {}: {}", savedUser.getId(), e.getMessage(), e);
        }
        // Use the mapper here as well
        return userMapper.userToUserResponseDTO(savedUser);
    }

    /**
     * A helper method to serialize the protobuf message using the same serializer that Kafka would use.
     * This requires us to manually instantiate and configure the serializer.
     */
    private byte[] serializePayload(Message payload) {
        try (Serializer<Message> serializer = new KafkaProtobufSerializer<>()) {
            // Configure the serializer with the Schema Registry URL.
            // The second argument 'false' indicates that this is for a value, not a key.
            serializer.configure(Map.of("schema.registry.url", schemaRegistryUrl), false);

            // Now, the serializer knows where to find the Schema Registry.
            // The topic is null as it's not strictly needed for serialization itself,
            // though the serializer might use it for subject name strategy if configured.
            return serializer.serialize(userRegisteredTopic, payload);
        }
    }

    private UserRegisteredEvent buildUserRegisteredEvent(User user) {
        UserRegisteredEvent.Builder eventBuilder = UserRegisteredEvent.newBuilder()
                .setUserId(user.getId())
                .setRegistrationTimestamp(System.currentTimeMillis())
                .setNickname(user.getNickname());

        if (user.getEmail() != null) {
            eventBuilder.setEmail(user.getEmail());
        }
        if (user.getRegistrationLatitude() != null && user.getRegistrationLongitude() != null) {
            eventBuilder.setRegistrationLocation(GeoLocation.newBuilder()
                    .setLatitude(user.getRegistrationLatitude())
                    .setLongitude(user.getRegistrationLongitude())
                    .build());
        }
        // ... set other fields for the event
        return eventBuilder.build();
    }
} 