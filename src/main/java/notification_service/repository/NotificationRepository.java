package notification_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.lettuce.core.dynamic.annotation.Param;
import notification_service.model.Notification;

import java.util.*;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // For the frontend to display the user's notification bell drop-down
    List<Notification> findByUserIdAndUserReadStatusOrderByCreatedAtDesc(UUID userId, String userReadStatus);

    // To prevent processing the same Kafka event twice
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.userReadStatus='READ'
            WHERE
            n.id=:notificationId
            AND n.userId=:userId
            AND n.userReadStatus='UNREAD'
                """)
    int markAsRead(@Param("notificationId") UUID notificationId, @Param("userId") UUID userId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.userReadStatus='READ'
            WHERE
            n.userId=:userId
            AND n.userReadStatus='UNREAD'
                """)
    int markAllAsRead(@Param("userId") UUID userId);

}