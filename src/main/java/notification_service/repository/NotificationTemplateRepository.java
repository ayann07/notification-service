package notification_service.repository;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import notification_service.enums.DeliveryChannel;
import notification_service.model.NotificationTemplate;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findAllByEventTypeAndIsActiveTrue(String eventType);

    Optional<NotificationTemplate> findByEventType(String eventType);

    Optional<NotificationTemplate> findByEventTypeAndDeliveryChannel(String eventType, DeliveryChannel deliveryChannel);
}
