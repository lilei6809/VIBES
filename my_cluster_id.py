import uuid
import base64

# Generate a new UUID
my_uuid = uuid.uuid4()

# Convert UUID to bytes, then Base64 encode (URL-safe, no padding)
# For Kafka, it's often the standard Base64 encoding of the UUID bytes, not the hex string.
# Kafka's Uuid.randomUuid().toString() produces a URL-safe base64 string.
# The confluentinc/cp-kafka image's entrypoint script expects a base64 encoded UUID.
# Let's use the standard base64 encoding of the UUID's bytes.

# Get the UUID as bytes (16 bytes)
uuid_bytes = my_uuid.bytes

# Base64 encode these bytes. Use standard Base64.
# Kafka's tools often use a specific variant, but standard base64 of the bytes is a common interpretation.
# Kafka's Uuid.toString() actually uses a URL-safe Base64 variant without padding.
cluster_id = base64.urlsafe_b64encode(uuid_bytes).rstrip(b'=').decode('ascii')

print(cluster_id)