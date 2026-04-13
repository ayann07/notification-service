package notification_service.service;

import lombok.RequiredArgsConstructor;
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

    public NotificationTemplate getTemplateByEventType(String eventType) {
        return templateRepository.findByEventType(eventType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "NotificationTemplate not found for event: " + eventType));
    }

    public TemplateResponseDTO updateTemplate(String eventType, TemplateRequestDTO requestDTO) {
        NotificationTemplate existingEntity = templateRepository
                .findByEventTypeAndDeliveryChannel(eventType, requestDTO.getDeliveryChannel())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found for event type "
                                + eventType
                                + " and channel "
                                + requestDTO.getDeliveryChannel()));

        modelMapper.map(requestDTO, existingEntity);
        NotificationTemplate updatedEntity = templateRepository.save(existingEntity);

        return modelMapper.map(updatedEntity, TemplateResponseDTO.class);
    }

    public void deleteTemplate(String eventType) {
        NotificationTemplate existing = getTemplateByEventType(eventType);
        templateRepository.delete(existing);
    }
}
