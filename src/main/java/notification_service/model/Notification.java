package notification_service.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import notification_service.enums.DeliveryChannel;
import notification_service.enums.NetworkDeliveryStatus;
import notification_service.enums.UserReadStatus;

@Entity
@Table(name = "notifications")
@SQLDelete(sql = "UPDATE notifications SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // We store the UUID directly instead of a strict @ManyToOne relationship
    // to keep the database queries lightning fast and decoupled.
    @Column(name = "user_id", nullable = true)
    private UUID userId;

    @Column(name = "recipient_email", nullable = true)
    private String recipientEmail;

    @Column(name = "recipient_phone", nullable = true)
    private String recipientPhone;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "correlation_id")
    private String correlationId;
    // correlationId: When an upstream Payment Service starts a transaction, it
    // generates a random ID (like txn_999). It passes that ID through Kafka to you.
    // If a user complains, "I never got my payment email!", you can search your
    // database for correlation_id = 'txn_999' and instantly see exactly what
    // happened to it.

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;
    // idempotencyKey: Networks are messy. Sometimes Kafka will accidentally send
    // you the exact same PAYMENT_FAILED event twice. The upstream service includes
    // an idempotencyKey. Because you marked it unique = true, if Kafka sends it
    // twice, PostgreSQL will instantly block the duplicate, ensuring you never
    // accidentally email a user twice for the same event.

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "delivery_channel", nullable = false)
    private DeliveryChannel deliveryChannel;
    // Why it is a single String: In your templates table, channels are a JSON array
    // (["EMAIL", "SMS"]). Here, it is just a single string ("EMAIL"). Why the
    // difference?

    // The Architecture Reason: If a Kafka event comes in that needs to be sent via
    // both Email and SMS, your Java code will actually generate two separate rows
    // in this notifications table.

    // Row 1: deliveryChannel = "EMAIL", networkDeliveryStatus = "SENT"

    // Row 2: deliveryChannel = "SMS", networkDeliveryStatus = "FAILED"
    // You must split them into separate rows because an email might succeed while
    // the SMS network goes down. Tracking them independently allows you to retry
    // the SMS without accidentally spamming the user with a second email.

    private Short priority;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;
    // The Text: Notice how this is different from the NotificationTemplate. The
    // template has {first_name} placeholders. This table stores the final, hydrated
    // string: "Hello Ayan, your payment failed."

    // Hypersistence Utils maps the PostgreSQL JSONB directly to a Java Map
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    // The Metadata: You save the raw variables ({"first_name": "Ayan", "amount":
    // 500}) into this jsonb column. Why? For auditing! If a template was broken and
    // sent the wrong message, you can look at the metadata column to see exactly
    // what data the upstream service actually sent you at that exact millisecond.

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "user_read_status")
    @Builder.Default
    private UserReadStatus userReadStatus = UserReadStatus.UNREAD;
    // User Status (UNREAD, READ, DISMISSED): Did the user actually open their
    // mobile app and look at the little bell icon? This tracks human behavior.

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "network_delivery_status")
    @Builder.Default
    private NetworkDeliveryStatus networkDeliveryStatus = NetworkDeliveryStatus.PENDING;
    // Network Status (PENDING, SENT, FAILED): Did your code successfully hand the
    // email off to SendGrid or the SMS off to Twilio? This tracks your backend
    // infrastructure.

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}