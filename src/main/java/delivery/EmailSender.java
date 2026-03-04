package delivery;

import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Notification;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSender implements ChannelSender {

    private final SesClient sesClient;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    @Override
    public String getChannelType() {
        return "EMAIL";
    }

    @Override
    public void send(Notification notification) {
        log.info("Initiating AWS SES API call for Notification ID: {}", notification.getId());
        String toAddress = "raza.ayan2002@gmail.com";

        try {
            SendEmailRequest request = SendEmailRequest.builder().source(fromEmail).destination(d -> d.toAddresses(
                    toAddress)).message(m -> m
                            .subject(s -> s.data(notification.getTitle()))
                            .body(b -> b.text(t -> t.data(notification.getMessage()))))
                    .build();
            SendEmailResponse response = sesClient.sendEmail(request);
            log.info("AWS ses email sent successfully! message ID :{} ", response.messageId());
        } catch (SesException e) {
            log.error("AWS SES Failed: {}", e.awsErrorDetails().errorMessage());
            throw new RuntimeException("SES Email failed to send", e);
        }
    }
}
