package notification_service.model;

import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
// @Entity: This tells Spring Boot, "This is not just a normal Java class. This
// represents a table in the database.
@Table(name = "users")
// @Table(name = "users"): While @Entity tells Spring it is a table, this
// specifies exactly what the table should be named in PostgreSQL.
// 1. Intercepts the delete command and does an update instead
@SQLDelete(sql = "UPDATE users SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
// @SQLDelete(sql = ...): This is your Soft Delete mechanism. If you call
// repository.delete(user), Hibernate normally runs DELETE FROM users WHERE id =
// ?. This annotation intercepts that command and replaces it with the UPDATE
// statement you provided, keeping the data safe but marking it as deleted.
@SQLRestriction("is_deleted = false")
// @SQLRestriction("is_deleted = false"): This is the second half of the soft
// delete. It automatically appends AND is_deleted = false to the end of every
// single SELECT query you ever write. You never have to worry about
// accidentally fetching a deleted user; Hibernate hides them from you
// automatically.
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
// @Builder: This unlocks the extremely clean "Builder Pattern" for creating new
// users in your code. Instead of writing User u = new User("a@b.com", "123");,
// you can write:
// User.builder().email("a@b.com").phoneNumber("123").build();
@NoArgsConstructor
// @NoArgsConstructor: Generates a completely empty constructor (public User()
// {}). JPA strictly requires this to function, as it needs an empty shell to
// pour database data into.
@AllArgsConstructor
// @AllArgsConstructor: Generates a constructor with every single field. The
// @Builder annotation requires this to work.
public class User extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = true)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number", length = 10, unique = true)
    private String phoneNumber;
}
