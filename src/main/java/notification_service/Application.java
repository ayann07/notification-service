package notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@EnableJpaAuditing
// When we created your BaseEntity class earlier, we put these two annotations
// on your date fields:
// @CreatedDate
// @LastModifiedDate
// without this annotation, these 2 will not work and with this automatically
// inject the exact LocalDateTime.now() into those columns before the data hits
// PostgreSQL.
@OpenAPIDefinition(info = @Info(title = "Notification Service API", version = "1.0", description = "Omnichannel routing engine for SMS, Email, and Push"))
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
