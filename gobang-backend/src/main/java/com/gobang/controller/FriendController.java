package com.gobang.controller;

import com.gobang.model.dto.ResponseDto;
import com.gobang.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友系统控制器
 * 处理好友相关API
 */
@RestController
@RequestMapping("/api/v2/friends")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FriendController {

    private static final Logger logger = LoggerFactory.getLogger(FriendController.class);

    private final UserServiceImpl userService;

    /**
     * 获取好友列表
     * GET /api/v2/friends/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseDto getFriendList(@PathVariable Long userId) {
        logger.info("获取好友列表: userId={}", userId);
        try {
            List<Map<String, Object>> friends = userService.getFriendList(userId);
            return ResponseDto.success(friends);
        } catch (Exception e) {
            logger.error("获取好友列表失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 添加好友
     * POST /api/v2/friends/request
     */
    @PostMapping("/request")
    public ResponseDto sendFriendRequest(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Long targetUserId = Long.valueOf(request.get("targetUserId").toString());
        String message = (String) request.get("message");

        logger.info("发送好友请求: userId -> targetUserId, message={}", userId, targetUserId, message);
        try {
            userService.sendFriendRequest(userId, targetUserId, message);
            return ResponseDto.success("好友请求已发送");
        } catch (Exception e) {
            logger.error("发送好友请求失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 获取好友请求列表
     * GET /api/v2/friends/requests/{userId}
     */
    @GetMapping("/requests/{userId}")
    public ResponseDto getFriendRequests(@PathVariable Long userId) {
        logger.info("获取好友请求: userId={}", userId);
        try {
            List<Map<String, Object>> requests = userService.getFriendRequests(userId);
            return ResponseDto.success(requests);
        } catch (Exception e) {
            logger.error("获取好友请求失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 处理好友请求
     * PUT /api/v2/friends/request/{requestId}?accept={accept}
     */
    @PutMapping("/request/{requestId}")
    public ResponseDto handleFriendRequest(
            @PathVariable Long requestId,
            @RequestParam Long userId,
            @RequestParam boolean accept
    ) {
        logger.info("处理好友请求: requestId={}, userId={}, accept={}", requestId, userId, accept);
        try {
            userService.handleFriendRequest(requestId, userId);
            return ResponseDto.success(accept ? "已添加好友" : "已拒绝请求");
        } catch (Exception e) {
            logger.error("处理好友请求失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 删除好友
     * DELETE /api/v2/friends/{userId}/{friendId}
     */
    @DeleteMapping("/{userId}/{friendId}")
    public ResponseDto deleteFriend(
            @PathVariable Long userId,
            @PathVariable Long friendId
    ) {
        logger.info("删除好友: userId -> friendId", userId, friendId);
        try {
            userService.deleteFriend(userId, friendId);
            return ResponseDto.success("已删除好友");
        } catch (Exception e) {
            logger.error("删除好友失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }
}
