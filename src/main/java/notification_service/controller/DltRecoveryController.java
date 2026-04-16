package notification_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import notification_service.dto.DltReplayRequest;
import notification_service.dto.DltReplayResponse;
import notification_service.service.DltReplayService;

@RestController
@RequestMapping("/internal/recovery/dlt")
@RequiredArgsConstructor
public class DltRecoveryController {

    private final DltReplayService dltReplayService;

    @PostMapping("/replay")
    public ResponseEntity<DltReplayResponse> replay(@RequestBody DltReplayRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(dltReplayService.replay(request, authentication));
    }
}
