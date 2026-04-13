package notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import notification_service.dto.TemplateRequestDTO;
import notification_service.dto.TemplateResponseDTO;
import notification_service.enums.DeliveryChannel;
import notification_service.exceptions.InvalidRequestException;
import notification_service.exceptions.ResourceConflictException;
import notification_service.exceptions.ResourceNotFoundException;
import notification_service.model.NotificationTemplate;
import notification_service.repository.NotificationTemplateRepository;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {
    // This class tests business rules in TemplateService directly.
    // We mock the repository and mapper so we can focus only on service logic.

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private TemplateService templateService;

    @Test
    void createTemplateSavesWhenNoDuplicateExists() {
        NotificationTemplate template = template();
        // Arrange repository behavior: "no duplicate exists, save succeeds."
        when(templateRepository.findByEventTypeAndDeliveryChannel("ORDER_SHIPPED", DeliveryChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(templateRepository.save(template)).thenReturn(template);

        NotificationTemplate saved = templateService.createTemplate(template);

        assertEquals(template, saved);
        verify(templateRepository).save(template);
    }

    @Test
    void createTemplateThrowsConflictForDuplicateEventAndChannel() {
        NotificationTemplate template = template();
        when(templateRepository.findByEventTypeAndDeliveryChannel("ORDER_SHIPPED", DeliveryChannel.EMAIL))
                .thenReturn(Optional.of(template));

        // Because the template already exists for the same event + channel, the
        // service should block the creation and never call save().
        assertThrows(ResourceConflictException.class, () -> templateService.createTemplate(template));
        verify(templateRepository, never()).save(any());
    }

    @Test
    void getTemplateReturnsExactEventAndChannelMatch() {
        NotificationTemplate template = template();
        when(templateRepository.findByEventTypeAndDeliveryChannel("ORDER_SHIPPED", DeliveryChannel.EMAIL))
                .thenReturn(Optional.of(template));

        NotificationTemplate found = templateService.getTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL);

        assertEquals(template, found);
    }

    @Test
    void getTemplateThrowsNotFoundWhenTemplateDoesNotExist() {
        when(templateRepository.findByEventTypeAndDeliveryChannel("ORDER_SHIPPED", DeliveryChannel.EMAIL))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.getTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL));
    }

    @Test
    void updateTemplateThrowsWhenPathEventTypeDoesNotMatchBody() {
        TemplateRequestDTO request = request();
        request.setEventType("PASSWORD_RESET");

        assertThrows(InvalidRequestException.class,
                () -> templateService.updateTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL, request));
        verify(templateRepository, never()).findByEventTypeAndDeliveryChannel(any(), any());
    }

    @Test
    void updateTemplateThrowsWhenPathChannelDoesNotMatchBody() {
        TemplateRequestDTO request = request();
        request.setDeliveryChannel(DeliveryChannel.SMS);

        assertThrows(InvalidRequestException.class,
                () -> templateService.updateTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL, request));
        verify(templateRepository, never()).findByEventTypeAndDeliveryChannel(any(), any());
    }

    @Test
    void updateTemplateReturnsMappedResponseForMatchingIdentity() {
        TemplateRequestDTO request = request();
        NotificationTemplate existing = template();
        TemplateResponseDTO response = TemplateResponseDTO.builder()
                .eventType("ORDER_SHIPPED")
                .deliveryChannel(DeliveryChannel.EMAIL)
                .title("Order shipped")
                .body("Updated body")
                .defaultPriority(3)
                .build();

        when(templateRepository.findByEventTypeAndDeliveryChannel("ORDER_SHIPPED", DeliveryChannel.EMAIL))
                .thenReturn(Optional.of(existing));
        when(templateRepository.save(existing)).thenReturn(existing);
        when(modelMapper.map(eq(existing), eq(TemplateResponseDTO.class))).thenReturn(response);

        // When the path identity and body identity match, update should proceed and
        // return the mapped response DTO.
        TemplateResponseDTO updated = templateService.updateTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL, request);

        assertEquals(response, updated);
        verify(modelMapper).map(request, existing);
    }

    @Test
    void deleteTemplateDeletesOnlyResolvedTemplate() {
        NotificationTemplate existing = template();
        when(templateRepository.findByEventTypeAndDeliveryChannel("ORDER_SHIPPED", DeliveryChannel.EMAIL))
                .thenReturn(Optional.of(existing));

        templateService.deleteTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL);

        verify(templateRepository).delete(existing);
    }

    private NotificationTemplate template() {
        // Reusable valid entity for tests that need a template already stored/found.
        return NotificationTemplate.builder()
                .eventType("ORDER_SHIPPED")
                .title("Order shipped")
                .body("Your order is on the way")
                .deliveryChannel(DeliveryChannel.EMAIL)
                .defaultPriority((short) 3)
                .build();
    }

    private TemplateRequestDTO request() {
        // Reusable valid request body for update scenarios.
        TemplateRequestDTO request = new TemplateRequestDTO();
        request.setEventType("ORDER_SHIPPED");
        request.setTitle("Order shipped");
        request.setBody("Your order is on the way");
        request.setDeliveryChannel(DeliveryChannel.EMAIL);
        request.setDefaultPriority(3);
        return request;
    }
}
