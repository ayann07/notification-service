package notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestUserDetails {

    @NotBlank(message = "firstName is required")
    private String firstName;

    private String lastName;

    @NotBlank(message = "email is required")
    private String email;

    @NotBlank(message = "phoneNumber is required")
    private String phoneNumber;
}
