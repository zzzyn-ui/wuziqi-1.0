package com.gobang.controller;

import com.gobang.core.room.RoomManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 清理控制器
 * 用于清理测试数据
 */
@RestController
@RequestMapping("/api/cleanup")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CleanupController {

    private static final Logger logger = LoggerFactory.getLogger(CleanupController.class);

    private final RoomManager roomManager;

    /**
     * 清理旧房间，只保留指定房间
     * GET /api/cleanup/rooms/{keepRoomId}
     */
    @GetMapping("/rooms/{keepRoomId}")
    public Map<String, Object> cleanupRooms(@PathVariable String keepRoomId) {
        logger.info("清理房间，保留房间: {}", keepRoomId);

        try {
            // 获取所有可观战房间
            var allRooms = roomManager.getObservableRooms();
            logger.info("当前可观战房间总数: {}", allRooms.size());

            int deletedCount = 0;
            for (var room : allRooms) {
                String roomId = room.getRoomId();
                if (!roomId.equals(keepRoomId)) {
                    // 从RoomManager中移除房间
                    roomManager.removeRoom(roomId);
                    deletedCount++;
                    logger.info("已删除房间: {}", roomId);
                }
            }

            return Map.of(
                "code", 200,
                "success", true,
                "message", "清理完成，删除了 " + deletedCount + " 个房间，保留了房间 " + keepRoomId
            );
        } catch (Exception e) {
            logger.error("清理房间失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "清理失败: " + e.getMessage()
            );
        }
    }
}
