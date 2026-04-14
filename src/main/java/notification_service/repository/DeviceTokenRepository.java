package notification_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import notification_service.model.DeviceToken;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    // Used by push delivery to fetch all devices that belong to a user.
    List<DeviceToken> findAllByUserId(UUID userId);

    // Used during registration so we can avoid storing the same token twice.
    Optional<DeviceToken> findByToken(String token);

    // Used during unregister to make sure a user can delete only their own token.
    boolean existsByUserIdAndToken(UUID userId, String token);

    void deleteByToken(String token);

}
