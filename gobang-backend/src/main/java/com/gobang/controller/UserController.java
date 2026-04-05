package com.gobang.controller;

import com.gobang.model.dto.ResponseDto;
import com.gobang.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户控制器
 * 处理用户相关API
 */
@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserServiceImpl userService;

    /**
     * 获取用户信息
     * GET /api/v2/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseDto getUserInfo(@PathVariable Long id) {
        logger.info("获取用户信息: userId={}", id);
        try {
            return ResponseDto.success(userService.getUserById(id));
        } catch (Exception e) {
            logger.error("获取用户信息失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 更新用户信息
     * PUT /api/v2/users/{id}
     */
    @PutMapping("/{id}")
    public ResponseDto updateUserInfo(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates
    ) {
        logger.info("更新用户信息: userId={}, updates={}", id, updates);
        try {
            String nickname = (String) updates.get("nickname");
            if (nickname != null && !nickname.trim().isEmpty()) {
                userService.updateUserNickname(id, nickname);
            }
            return ResponseDto.success(userService.getUserById(id));
        } catch (Exception e) {
            logger.error("更新用户信息失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 获取用户统计信息
     * GET /api/v2/users/{id}/stats
     */
    @GetMapping("/{id}/stats")
    public ResponseDto getUserStats(@PathVariable Long id) {
        logger.info("获取用户统计: userId={}", id);
        try {
            return ResponseDto.success(userService.getUserStats(id));
        } catch (Exception e) {
            logger.error("获取用户统计失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 搜索用户
     * GET /api/v2/users/search?q={query}
     */
    @GetMapping("/search")
    public ResponseDto searchUsers(@RequestParam String q) {
        logger.info("搜索用户: query={}", q);
        try {
            return ResponseDto.success(userService.searchUsers(q));
        } catch (Exception e) {
            logger.error("搜索用户失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }
}
