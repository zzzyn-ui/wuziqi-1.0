package com.gobang.controller;

import com.gobang.model.dto.LoginDto;
import com.gobang.model.dto.RegisterDto;
import com.gobang.model.entity.User;
import com.gobang.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API 认证控制器
 * 提供 HTTP REST 接口用于登录和注册
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserServiceImpl userService;

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginDto loginDto) {
        logger.info("用户登录请求: {}", loginDto.getUsername());

        try {
            String token = userService.login(loginDto);
            User user = userService.getUserByToken(token);

            return Map.of(
                "success", true,
                "token", token,
                "user", user
            );
        } catch (Exception e) {
            logger.error("登录失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterDto registerDto) {
        logger.info("用户注册请求: {}", registerDto.getUsername());

        try {
            userService.register(registerDto);
            return Map.of(
                "success", true,
                "message", "注册成功"
            );
        } catch (Exception e) {
            logger.error("注册失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", e.getMessage()
            );
        }
    }
}
