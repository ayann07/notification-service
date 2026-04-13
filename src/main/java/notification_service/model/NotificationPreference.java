package notification_service.model;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@SQLDelete(sql = "UPDATE notification_preferences SET is_deleted = true, deleted_at = NOW() WHERE user_id = ?")
@SQLRestriction("is_deleted = false")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference extends BaseEntity {

    // The User ID is actually the Primary Key here because of the 1-to-1
    // relationship
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "muted_channels", columnDefinition = "jsonb")
    @Builder.Default
    private Set<String> mutedChannels = new HashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    // When saving to the DB: It takes your Java List<String> ["PROMO", "DIGEST"]
    // and serializes it into a JSON string ["PROMO", "DIGEST"] so PostgreSQL can
    // store it in the jsonb column.

    // When reading from the DB: It reads the jsonb string from PostgreSQL and
    // automatically deserializes it back into a beautiful Java List<String> for
    // your code to use.
    @Column(name = "muted_events", columnDefinition = "jsonb")
    // This tells your database how to store the data physically.
    @Builder.Default
    // Because you put @Builder at the top of your class, Lombok generates a builder
    // pattern for you. However, Lombok's builder has a quirk: it ignores your =
    // List.of() initialization and will insert null anyway!
    // Adding @Builder.Default forces Lombok to respect your code. It says: "If I
    // build a new User Preference and I don't explicitly provide a list of muted
    // events, please use the empty List.of() as the default."
    private Set<String> mutedEvents = new HashSet<>();
    // in your Java code, you just want to work with a simple list. You want to be
    // able to say mutedEvents.add("WEEKLY_DIGEST") or
    // mutedEvents.contains("PAYMENT_FAILED"). List.of() just ensures that if a user
    // has never muted anything, the list starts completely empty instead of being
    // null (which causes nasty NullPointerExceptions).
}