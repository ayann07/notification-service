package notification_service.config;

import java.io.InputStream;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    // Run this method immediately after Spring Boot is finished constructing this
    // class
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {

                // Sometimes, especially if you use Spring Boot DevTools to hot-reload your code
                // when you save a file, Spring will try to run this setup method twice. If you
                // try to log into Firebase when you are already logged in, it throws an ugly
                // crash.

                // Read the JSON file from the resources folder
                InputStream serviceAccount = new ClassPathResource("firebase-adminsdk.json").getInputStream();
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                // FirebaseOptions.builder()...: This takes the raw JSON keys we just read and
                // packages them into a format that Google's Firebase SDK understands.

                FirebaseApp.initializeApp(options);
                // FirebaseApp.initializeApp(options):This method takes your packaged keys,
                // reaches out across the internet to Google, and officially authenticates your
                // Spring Boot server.
                log.info("Firebase Admin SDK initialized successfully.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize firebase: {} ", e.getMessage());
        }

    }

}
