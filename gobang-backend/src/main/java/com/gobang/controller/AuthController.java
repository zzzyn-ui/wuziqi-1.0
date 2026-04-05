package com.gobang.controller;

import com.gobang.model.dto.LoginDto;
import com.gobang.model.dto.RegisterDto;
import com.gobang.model.dto.ResponseDto;
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
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserServiceImpl userService;

    /**
     * 用户登录
     * POST /api/v2/auth/login
     */
    @PostMapping("/login")
    public ResponseDto login(@RequestBody LoginDto loginDto) {
        logger.info("用户登录请求: {}", loginDto.getUsername());

        try {
            String token = userService.login(loginDto);
            User user = userService.getUserByToken(token);

            return ResponseDto.success(Map.of(
                "token", token,
                "user", user
            ));
        } catch (Exception e) {
            logger.error("登录失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 用户注册
     * POST /api/v2/auth/register
     */
    @PostMapping("/register")
    public ResponseDto register(@RequestBody RegisterDto registerDto) {
        logger.info("用户注册请求: {}", registerDto.getUsername());

        try {
            userService.register(registerDto);
            return ResponseDto.success("注册成功");
        } catch (Exception e) {
            logger.error("注册失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }
}
