package com.gobang.auth;

import com.gobang.common.ApiResponse;
import com.gobang.model.dto.LoginDto;
import com.gobang.model.dto.RegisterDto;
import com.gobang.model.entity.User;
import com.gobang.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {
    private final UserServiceImpl userService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginDto dto) {
        String token = userService.login(dto);
        User user = userService.getUserByToken(token);
        return ApiResponse.success(Map.of("token", token, "user", user));
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody RegisterDto dto) {
        userService.register(dto);
        return ApiResponse.success("注册成功");
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        return ApiResponse.success("退出成功");
    }
}
