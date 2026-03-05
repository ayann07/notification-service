package notification_service.delivery;

import notification_service.model.Notification;

public interface ChannelSender {

    // This tells us if this class handles "EMAIL", "SMS", or "PUSH"
    String getChannelType();

    // The actual method that fires the message
    void send(Notification notification);

}
