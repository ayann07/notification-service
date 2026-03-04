package delivery;

import model.Notification;

// @Slf4j
// @Service
public class SmsSender implements ChannelSender {

    @Override
    public String getChannelType() {
        return "SMS";
    }

    @Override
    public void send(Notification notification) {

    }

}
