package cc.lingnow.controller;

import cc.lingnow.model.UserEntity;
import cc.lingnow.service.AuthService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        UserEntity user = authService.register(request.getUsername(), request.getPassword());
        StpUtil.login(user.getId());
        return ResponseEntity.ok(Map.of(
            "username", user.getUsername(),
            "token", StpUtil.getTokenValue()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        return authService.login(request.getUsername(), request.getPassword())
                .map(user -> {
                    StpUtil.login(user.getId());
                    return ResponseEntity.ok(Map.of(
                        "username", user.getUsername(),
                        "token", StpUtil.getTokenValue()
                    ));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @Data
    public static class AuthRequest {
        private String username;
        private String password;
    }
}
