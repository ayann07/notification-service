package notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
// When we created your BaseEntity class earlier, we put these two annotations
// on your date fields:
// @CreatedDate
// @LastModifiedDate
// without this annotation, these 2 will not work and with this automatically
// inject the exact LocalDateTime.now() into those columns before the data hits
// PostgreSQL.
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
