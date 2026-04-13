package com.gobang.controller;

import com.gobang.model.dto.ObserverRoomDto;
import com.gobang.service.ObserverService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 观战模式控制器
 * 提供观战相关的REST API
 */
@RestController
@RequestMapping("/api/observer")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ObserverController {

    private static final Logger logger = LoggerFactory.getLogger(ObserverController.class);

    private final ObserverService observerService;

    /**
     * 获取可观的战房间列表
     * GET /api/observer/rooms
     */
    @GetMapping("/rooms")
    public Map<String, Object> getObservableRooms() {
        logger.info("获取可观战房间列表");

        try {
            List<Map<String, Object>> rooms = observerService.getObservableRooms();
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of(
                    "list", rooms,
                    "total", rooms.size()
                ),
                "message", "获取成功"
            );
        } catch (Exception e) {
            logger.error("获取可观战房间列表失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 加入观战
     * POST /api/observer/join
     */
    @PostMapping("/join")
    public Map<String, Object> joinObserver(@RequestBody Map<String, Object> payload) {
        String roomId = (String) payload.get("roomId");
        Long userId = Long.valueOf(payload.get("userId").toString());

        logger.info("用户 {} 加入观战: roomId={}", userId, roomId);

        try {
            ObserverRoomDto roomDto = observerService.joinObserver(roomId, userId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", roomDto,
                "message", "加入观战成功"
            );
        } catch (Exception e) {
            logger.error("加入观战失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 离开观战
     * POST /api/observer/leave
     */
    @PostMapping("/leave")
    public Map<String, Object> leaveObserver(@RequestBody Map<String, Object> payload) {
        String roomId = (String) payload.get("roomId");
        Long userId = Long.valueOf(payload.get("userId").toString());

        logger.info("用户 {} 离开观战: roomId={}", userId, roomId);

        try {
            observerService.leaveObserver(roomId, userId);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "离开观战成功"
            );
        } catch (Exception e) {
            logger.error("离开观战失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 获取房间观战者列表
     * GET /api/observer/{roomId}/observers
     */
    @GetMapping("/{roomId}/observers")
    public Map<String, Object> getObservers(@PathVariable String roomId) {
        logger.info("获取房间观战者列表: roomId={}", roomId);

        try {
            List<Long> observers = observerService.getObservers(roomId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of(
                    "observers", observers,
                    "count", observers.size()
                ),
                "message", "获取成功"
            );
        } catch (Exception e) {
            logger.error("获取观战者列表失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 获取房间观战者数量
     * GET /api/observer/{roomId}/count
     */
    @GetMapping("/{roomId}/count")
    public Map<String, Object> getObserverCount(@PathVariable String roomId) {
        try {
            int count = observerService.getObserverCount(roomId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of("count", count),
                "message", "获取成功"
            );
        } catch (Exception e) {
            logger.error("获取观战者数量失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }
}
