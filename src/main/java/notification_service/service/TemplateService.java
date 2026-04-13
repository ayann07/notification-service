package notification_service.service;

import lombok.RequiredArgsConstructor;
import notification_service.enums.DeliveryChannel;
import notification_service.exceptions.InvalidRequestException;
import notification_service.exceptions.ResourceConflictException;
import notification_service.exceptions.ResourceNotFoundException;
import notification_service.dto.TemplateRequestDTO;
import notification_service.dto.TemplateResponseDTO;
import notification_service.model.NotificationTemplate;
import notification_service.repository.NotificationTemplateRepository;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final ModelMapper modelMapper;

    public NotificationTemplate createTemplate(NotificationTemplate template) {
        if (templateRepository.findByEventTypeAndDeliveryChannel(
                template.getEventType(),
                template.getDeliveryChannel()).isPresent()) {
            throw new ResourceConflictException(
                    "NotificationTemplate already exists for event type "
                            + template.getEventType()
                            + " and channel "
                            + template.getDeliveryChannel()
                            + "!");
        }
        return templateRepository.save(template);
    }

    public List<NotificationTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    public NotificationTemplate getTemplate(String eventType, DeliveryChannel deliveryChannel) {
        return templateRepository.findByEventTypeAndDeliveryChannel(eventType, deliveryChannel)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found for event type "
                                + eventType
                                + " and channel "
                                + deliveryChannel));
    }

    public TemplateResponseDTO updateTemplate(
            String eventType,
            DeliveryChannel deliveryChannel,
            TemplateRequestDTO requestDTO) {

        validateTemplateIdentity(eventType, deliveryChannel, requestDTO);
        NotificationTemplate existingEntity = getTemplate(eventType, deliveryChannel);

        modelMapper.map(requestDTO, existingEntity);
        NotificationTemplate updatedEntity = templateRepository.save(existingEntity);

        return modelMapper.map(updatedEntity, TemplateResponseDTO.class);
    }

    public void deleteTemplate(String eventType, DeliveryChannel deliveryChannel) {
        NotificationTemplate existing = getTemplate(eventType, deliveryChannel);
        templateRepository.delete(existing);
    }

    private void validateTemplateIdentity(
            String eventType,
            DeliveryChannel deliveryChannel,
            TemplateRequestDTO requestDTO) {

        if (!eventType.equals(requestDTO.getEventType())) {
            throw new InvalidRequestException("Path eventType must match request body eventType");
        }

        if (deliveryChannel != requestDTO.getDeliveryChannel()) {
            throw new InvalidRequestException("Path deliveryChannel must match request body deliveryChannel");
        }
    }
}
