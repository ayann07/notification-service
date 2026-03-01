package model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

@Data
// @Data: This is a Lombok shortcut. Behind the scenes, it automatically
// generates all the getCreatedAt(), setCreatedAt(), getUpdatedAt(), etc., for
// this class. You never have to write getter or setter methods manually.
@MappedSuperclass
// @MappedSuperclass: This is the most important annotation here. It tells
// Hibernate: "Do NOT create a table called base_entity in PostgreSQL. Instead,
// take all the columns inside this class and copy-paste them into the tables of
// any class that extends it (like users or notifications)."
@EntityListeners(AuditingEntityListener.class)
// @EntityListeners(AuditingEntityListener.class): This is the "receiver" for
// the @EnableJpaAuditing switch we turned on earlier. It tells Spring to
// actively watch this class. Whenever a row is about to be saved or updated in
// the database, this listener steps in and says, "Wait! Let me fill in the
// timestamps first."
public abstract class BaseEntity {
    @CreatedDate
    // @CreatedDate: When you call repository.save(user)for the very first time (an
    // SQL INSERT), the Auditing system sees this annotation and instantly injects
    // the exact millisecond LocalDateTime.now() into this variable.
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}