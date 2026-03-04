package repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import model.NotificationPreference;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    // We don't need any custom methods here yet!
    // JpaRepository already gives us findById(UUID userId) out of the box.
}
