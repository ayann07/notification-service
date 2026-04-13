package notification_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import notification_service.dto.TemplateRequestDTO;
import notification_service.enums.DeliveryChannel;
import notification_service.exceptions.GlobalExceptionHandler;
import notification_service.exceptions.InvalidRequestException;
import notification_service.exceptions.ResourceConflictException;
import notification_service.exceptions.ResourceNotFoundException;
import notification_service.model.NotificationTemplate;
import notification_service.service.TemplateService;

@ExtendWith(MockitoExtension.class)
class TemplateControllerTest {
    // This class tests the HTTP behavior of the template API without starting the
    // whole Spring application. We mock the service and focus only on the
    // controller + validation + exception handler behavior.

    @Mock
    private TemplateService templateService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TemplateController controller = new TemplateController(templateService, new ModelMapper());
        objectMapper = new ObjectMapper();

        // We enable bean validation here so @Valid on the controller behaves the same
        // way it would inside the real application.
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    void getTemplateReturnsTemplateForEventTypeAndChannel() throws Exception {
        // Arrange: pretend the service found a matching template.
        NotificationTemplate template = NotificationTemplate.builder()
                .eventType("ORDER_SHIPPED")
                .title("Order shipped")
                .body("Your order is on the way")
                .deliveryChannel(DeliveryChannel.EMAIL)
                .defaultPriority((short) 3)
                .build();

        when(templateService.getTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL)).thenReturn(template);

        // Act + Assert: call the endpoint and verify the JSON response shape.
        mockMvc.perform(get("/templates/ORDER_SHIPPED/EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("ORDER_SHIPPED"))
                .andExpect(jsonPath("$.deliveryChannel").value("EMAIL"));
    }

    @Test
    void getTemplateReturnsNotFoundWhenTemplateDoesNotExist() throws Exception {
        // Arrange: the service throws our custom not-found exception.
        when(templateService.getTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL))
                .thenThrow(new ResourceNotFoundException("missing template"));

        // Assert: GlobalExceptionHandler should translate that into a 404 JSON error.
        mockMvc.perform(get("/templates/ORDER_SHIPPED/EMAIL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing template"));
    }

    @Test
    void createTemplateReturnsConflictWhenDuplicateExists() throws Exception {
        TemplateRequestDTO request = validRequest();
        // We do not care about the exact mapped entity here, only that the controller
        // turns the service-level conflict into the right HTTP response.
        when(templateService.createTemplate(any(NotificationTemplate.class)))
                .thenThrow(new ResourceConflictException("duplicate template"));

        mockMvc.perform(post("/templates")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("duplicate template"));
    }

    @Test
    void createTemplateReturnsValidationErrorsForBadRequestBody() throws Exception {
        // Arrange an invalid body on purpose so @Valid fails before the service is
        // even called.
        TemplateRequestDTO request = new TemplateRequestDTO();
        request.setEventType("");
        request.setTitle("");
        request.setBody("short");

        // Assert both the HTTP status and the field-level validation error payload.
        mockMvc.perform(post("/templates")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.eventType").exists())
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.body").exists())
                .andExpect(jsonPath("$.validationErrors.deliveryChannel").exists());
    }

    @Test
    void updateTemplateReturnsBadRequestWhenPathAndBodyDoNotMatch() throws Exception {
        TemplateRequestDTO request = validRequest();
        request.setEventType("PASSWORD_RESET");

        // The service enforces that the resource identity in the URL must match the
        // body identity. This protects us from updating the wrong template.
        when(templateService.updateTemplate("ORDER_SHIPPED", DeliveryChannel.EMAIL, request))
                .thenThrow(new InvalidRequestException("Path eventType must match request body eventType"));

        mockMvc.perform(put("/templates/ORDER_SHIPPED/EMAIL")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Path eventType must match request body eventType"));
    }

    private TemplateRequestDTO validRequest() {
        // Helper method so multiple tests can reuse the same valid request body.
        TemplateRequestDTO request = new TemplateRequestDTO();
        request.setEventType("ORDER_SHIPPED");
        request.setTitle("Order shipped");
        request.setBody("Your order is on the way");
        request.setDeliveryChannel(DeliveryChannel.EMAIL);
        request.setDefaultPriority(3);
        return request;
    }
}
