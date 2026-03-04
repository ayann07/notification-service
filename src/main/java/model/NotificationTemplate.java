package model;

import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_templates")
@SQLDelete(sql = "UPDATE notification_templates SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, unique = true)
    private String eventType;
    // Role: This is the exact string your upstream microservices will send over
    // Kafka (e.g., PAYMENT_FAILED or ORDER_SHIPPED).

    // Why unique? When Kafka says "Hey, an order shipped!", your Spring Boot code
    // will query the database: SELECT * FROM templates WHERE event_type =
    // 'ORDER_SHIPPED'. It must be unique so the database knows exactly which single
    // template to grab.

    @Column(name = "title_template", nullable = false)
    private String titleTemplate;

    @Column(name = "body_template", columnDefinition = "TEXT", nullable = false)
    private String bodyTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_channels", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> defaultChannels = List.of("IN_APP");

    // Role: This dictates where the message should go. A password reset might need
    // ["EMAIL", "SMS"], while a weekly digest only needs ["EMAIL"].

    // How it works: Your Java code will look at this array, compare it against the
    // user's notification_preferences (to see if they muted anything), and then
    // fire the messages to the correct platforms.

    @Column(name = "default_priority")
    @Builder.Default
    private Short defaultPriority = 3; // e.g., 1 = Critical, 3 = Normal, 5 = Low
    // Role: Determines how fast the system processes the message.

    // Why it matters: If your Kafka queue suddenly gets flooded with 50,000
    // WEEKLY_DIGEST events (Priority 5), and right in the middle of that, a user
    // requests a PASSWORD_RESET (Priority 1), your code uses this number to push
    // the password reset to the very front of the line so the user doesn't have to
    // wait 10 minutes for their email.

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
    // Role: A safety toggle to turn a notification on or off immediately.

    // Why it matters: Imagine the upstream Order Service has a bug and starts
    // spamming millions of fake ORDER_SHIPPED events. Instead of shutting down your
    // whole backend, you just go into the database and flip isActive = false for
    // that specific template. Your code will see the flag and instantly drop the
    // bad events until the upstream team fixes their bug.
}
