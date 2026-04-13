package notification_service.controller;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import notification_service.dto.TemplateRequestDTO;
import notification_service.dto.TemplateResponseDTO;
import notification_service.enums.DeliveryChannel;
import notification_service.model.NotificationTemplate;
import notification_service.service.TemplateService;

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final ModelMapper modelMapper; // Inject the Bean

    @GetMapping("/{eventType}/{deliveryChannel}")
    public ResponseEntity<TemplateResponseDTO> getTemplate(
            @PathVariable String eventType,
            @PathVariable DeliveryChannel deliveryChannel) {
        NotificationTemplate entity = templateService.getTemplate(eventType, deliveryChannel);

        TemplateResponseDTO dto = modelMapper.map(entity, TemplateResponseDTO.class);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponseDTO>> getAllTemplates() {
        List<TemplateResponseDTO> templates = templateService.getAllTemplates().stream()
                .map(template -> modelMapper.map(template, TemplateResponseDTO.class)).toList();
        return ResponseEntity.ok(templates);
    }

    @DeleteMapping("/{eventType}/{deliveryChannel}")
    public ResponseEntity<String> deleteTemplate(
            @PathVariable String eventType,
            @PathVariable DeliveryChannel deliveryChannel) {
        templateService.deleteTemplate(eventType, deliveryChannel);
        return ResponseEntity.ok("Template " + eventType + " for channel " + deliveryChannel + " deleted successfully.");
    }

    @PostMapping
    public ResponseEntity<TemplateResponseDTO> createTemplate(@Valid @RequestBody TemplateRequestDTO requestDTO) {
        NotificationTemplate entity = modelMapper.map(requestDTO, NotificationTemplate.class);
        NotificationTemplate savedEntity = templateService.createTemplate(entity);
        return ResponseEntity.ok(modelMapper.map(savedEntity, TemplateResponseDTO.class));
    }

    @PutMapping("/{eventType}/{deliveryChannel}")
    public ResponseEntity<TemplateResponseDTO> updateTemplate(
            @PathVariable String eventType,
            @PathVariable DeliveryChannel deliveryChannel,
            @Valid @RequestBody TemplateRequestDTO requestDTO) {

        return ResponseEntity.ok(templateService.updateTemplate(eventType, deliveryChannel, requestDTO));
    }
}
