package notification_service.service;

import lombok.RequiredArgsConstructor;
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
        if (templateRepository.findByEventType(template.getEventType()).isPresent()) {
            throw new RuntimeException(
                    "NotificationTemplate with event type " + template.getEventType() + " already exists!");
        }
        return templateRepository.save(template);
    }

    public List<NotificationTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    public NotificationTemplate getTemplateByEventType(String eventType) {
        return templateRepository.findByEventType(eventType)
                .orElseThrow(() -> new RuntimeException("NotificationTemplate not found for event: " + eventType));
    }

    public TemplateResponseDTO updateTemplate(String eventType, TemplateRequestDTO requestDTO) {
        NotificationTemplate existingEntity = templateRepository.findByEventType(eventType)
                .orElseThrow(() -> new RuntimeException("Template not found: " + eventType));

        modelMapper.map(requestDTO, existingEntity);
        NotificationTemplate updatedEntity = templateRepository.save(existingEntity);

        return modelMapper.map(updatedEntity, TemplateResponseDTO.class);
    }

    public void deleteTemplate(String eventType) {
        NotificationTemplate existing = getTemplateByEventType(eventType);
        templateRepository.delete(existing);
    }
}
