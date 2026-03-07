package notification_service.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification_service.enums.DeliveryChannel;
import notification_service.model.Notification;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsSender implements ChannelSender {

    @Value("${twilio.from-number}")
    private String fromNumber;

    @Override
    public DeliveryChannel getChannelType() {
        return DeliveryChannel.SMS;
    }

    @Override
    public void send(Notification notification) {
        log.info("Initiating twilio API call for Notification ID: {}", notification.getId());
        String toAddress = notification.getRecipientPhone();
        try {
            Message message = Message
                    .creator(new PhoneNumber(toAddress), new PhoneNumber(fromNumber), notification.getMessage())
                    .create();
            log.info(" Twilio SMS Delivered! Message SID: {}", message.getSid());
        } catch (Exception e) {
            log.error("Twilio sms failed : {}", e.getMessage());
            throw new RuntimeException("Twilio SMS failed to send", e);
        }
    }

}
