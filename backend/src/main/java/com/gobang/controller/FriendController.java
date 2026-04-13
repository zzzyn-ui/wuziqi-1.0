package com.gobang.controller;

import com.gobang.model.entity.Friend;
import com.gobang.model.entity.FriendGroup;
import com.gobang.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友系统控制器
 */
@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FriendController {

    private static final Logger logger = LoggerFactory.getLogger(FriendController.class);

    private final FriendService friendService;

    /**
     * 发送好友请求
     * POST /api/friend/request
     */
    @PostMapping("/request")
    public Map<String, Object> sendRequest(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        Long friendId = Long.valueOf(payload.get("friendId").toString());
        String message = (String) payload.get("message");

        logger.info("发送好友请求: userId={}, friendId={}", userId, friendId);

        try {
            Friend friend = friendService.sendRequest(userId, friendId, message);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of("id", friend.getId()),
                "message", "好友请求已发送"
            );
        } catch (Exception e) {
            logger.error("发送好友请求失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 接受好友请求
     * POST /api/friend/accept
     */
    @PostMapping("/accept")
    public Map<String, Object> acceptRequest(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        Long requestId = Long.valueOf(payload.get("requestId").toString());

        logger.info("接受好友请求: userId={}, requestId={}", userId, requestId);

        try {
            friendService.acceptRequest(userId, requestId);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "已接受好友请求"
            );
        } catch (Exception e) {
            logger.error("接受好友请求失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 拒绝好友请求
     * POST /api/friend/reject
     */
    @PostMapping("/reject")
    public Map<String, Object> rejectRequest(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        Long requestId = Long.valueOf(payload.get("requestId").toString());

        logger.info("拒绝好友请求: userId={}, requestId={}", userId, requestId);

        try {
            friendService.rejectRequest(userId, requestId);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "已拒绝好友请求"
            );
        } catch (Exception e) {
            logger.error("拒绝好友请求失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 获取好友列表
     * GET /api/friend/list?userId=xxx
     */
    @GetMapping("/list")
    public Map<String, Object> getFriendList(@RequestParam Long userId) {
        logger.info("获取好友列表: userId={}", userId);

        try {
            List<Map<String, Object>> friends = friendService.getFriendList(userId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of(
                    "list", friends,
                    "total", friends.size()
                ),
                "message", "获取成功"
            );
        } catch (Exception e) {
            logger.error("获取好友列表失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 获取待处理的好友请求
     * GET /api/friend/requests?userId=xxx
     */
    @GetMapping("/requests")
    public Map<String, Object> getPendingRequests(@RequestParam Long userId) {
        logger.info("获取待处理请求: userId={}", userId);

        try {
            List<Map<String, Object>> requests = friendService.getPendingRequests(userId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of(
                    "list", requests,
                    "total", requests.size()
                ),
                "message", "获取成功"
            );
        } catch (Exception e) {
            logger.error("获取待处理请求失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 删除好友
     * DELETE /api/friend/delete?userId=xxx&friendId=xxx
     */
    @DeleteMapping("/delete")
    public Map<String, Object> deleteFriend(@RequestParam Long userId, @RequestParam Long friendId) {
        logger.info("删除好友: userId={}, friendId={}", userId, friendId);

        try {
            friendService.deleteFriend(userId, friendId);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "已删除好友"
            );
        } catch (Exception e) {
            logger.error("删除好友失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 更新好友备注
     * PUT /api/friend/remark
     */
    @PutMapping("/remark")
    public Map<String, Object> updateRemark(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        Long friendId = Long.valueOf(payload.get("friendId").toString());
        String remark = (String) payload.get("remark");

        logger.info("更新好友备注: userId={}, friendId={}, remark={}", userId, friendId, remark);

        try {
            friendService.updateRemark(userId, friendId, remark);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "备注已更新"
            );
        } catch (Exception e) {
            logger.error("更新备注失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 搜索用户（用于添加好友）
     * GET /api/friend/search?userId=xxx&keyword=xxx
     */
    @GetMapping("/search")
    public Map<String, Object> searchUsers(@RequestParam Long userId, @RequestParam String keyword) {
        logger.info("搜索用户: userId={}, keyword={}", userId, keyword);

        try {
            List<Map<String, Object>> users = friendService.searchUsers(userId, keyword);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of(
                    "list", users,
                    "total", users.size()
                ),
                "message", "搜索成功"
            );
        } catch (Exception e) {
            logger.error("搜索用户失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "搜索失败: " + e.getMessage()
            );
        }
    }

    /**
     * 检查是否是好友
     * GET /api/friend/check?userId=xxx&friendId=xxx
     */
    @GetMapping("/check")
    public Map<String, Object> checkFriend(@RequestParam Long userId, @RequestParam Long friendId) {
        try {
            boolean isFriend = friendService.isFriend(userId, friendId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of("isFriend", isFriend)
            );
        } catch (Exception e) {
            logger.error("检查好友关系失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "检查失败: " + e.getMessage()
            );
        }
    }

    /**
     * 获取好友分组列表
     * GET /api/friend/groups?userId=xxx
     */
    @GetMapping("/groups")
    public Map<String, Object> getGroups(@RequestParam Long userId) {
        logger.info("获取好友分组: userId={}", userId);

        try {
            List<FriendGroup> groups = friendService.getGroups(userId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of(
                    "list", groups,
                    "total", groups.size()
                ),
                "message", "获取成功"
            );
        } catch (Exception e) {
            logger.error("获取分组失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }

    /**
     * 创建好友分组
     * POST /api/friend/group
     */
    @PostMapping("/group")
    public Map<String, Object> createGroup(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        String groupName = (String) payload.get("groupName");

        logger.info("创建好友分组: userId={}, groupName={}", userId, groupName);

        try {
            FriendGroup group = friendService.createGroup(userId, groupName);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of("id", group.getId()),
                "message", "分组创建成功"
            );
        } catch (Exception e) {
            logger.error("创建分组失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 删除好友分组
     * DELETE /api/friend/group?userId=xxx&groupId=xxx
     */
    @DeleteMapping("/group")
    public Map<String, Object> deleteGroup(@RequestParam Long userId, @RequestParam Integer groupId) {
        logger.info("删除好友分组: userId={}, groupId={}", userId, groupId);

        try {
            friendService.deleteGroup(userId, groupId);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "分组已删除"
            );
        } catch (Exception e) {
            logger.error("删除分组失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 移动好友到分组
     * PUT /api/friend/group/move
     */
    @PutMapping("/group/move")
    public Map<String, Object> moveFriendToGroup(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        Long friendId = Long.valueOf(payload.get("friendId").toString());
        Integer groupId = Integer.valueOf(payload.get("groupId").toString());

        logger.info("移动好友到分组: userId={}, friendId={}, groupId={}", userId, friendId, groupId);

        try {
            friendService.moveFriendToGroup(userId, friendId, groupId);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "好友已移动到指定分组"
            );
        } catch (Exception e) {
            logger.error("移动好友失败", e);
            return Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 获取在线好友数量
     * GET /api/friend/online/count?userId=xxx
     */
    @GetMapping("/online/count")
    public Map<String, Object> getOnlineFriendCount(@RequestParam Long userId) {
        try {
            int count = friendService.getOnlineFriendCount(userId);
            return Map.of(
                "code", 200,
                "success", true,
                "data", Map.of("count", count)
            );
        } catch (Exception e) {
            logger.error("获取在线好友数量失败", e);
            return Map.of(
                "code", 500,
                "success", false,
                "message", "获取失败: " + e.getMessage()
            );
        }
    }
}
