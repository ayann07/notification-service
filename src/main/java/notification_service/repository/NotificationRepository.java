package notification_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import notification_service.model.Notification;

import java.util.*;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // For the frontend to display the user's notification bell drop-down
    List<Notification> findByUserIdAndUserReadStatusOrderByCreatedAtDesc(UUID userId, String userReadStatus);

    // To prevent processing the same Kafka event twice
    boolean existsByIdempotencyKey(String idempotencyKey);
}