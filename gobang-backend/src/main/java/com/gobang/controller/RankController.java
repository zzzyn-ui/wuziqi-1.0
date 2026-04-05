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
 * 排行榜控制器
 * 处理排行榜相关API
 */
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RankController {

    private static final Logger logger = LoggerFactory.getLogger(RankController.class);

    private final UserServiceImpl userService;

    /**
     * 获取排行榜
     * GET /api/v2/rank/{type}
     *
     * @param type 排行榜类型: all(总榜), daily(日榜), weekly(周榜), monthly(月榜)
     */
    @GetMapping("/rank/{type}")
    public ResponseDto getRankList(@PathVariable String type) {
        logger.info("获取排行榜: type={}", type);
        try {
            List<Map<String, Object>> rankList = userService.getRankList(type, 50);

            // 获取当前用户排名
            Integer myRank = null;
            // TODO: 从token获取当前用户ID
            // Long currentUserId = getCurrentUserId();
            // if (currentUserId != null) {
            //     myRank = userService.getUserRank(currentUserId, type);
            // }

            return ResponseDto.success(Map.of(
                "list", rankList,
                "myRank", myRank != null ? myRank : 0
            ));
        } catch (Exception e) {
            logger.error("获取排行榜失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }

    /**
     * 获取用户排名
     * GET /api/v2/rank/{type}/user/{userId}
     */
    @GetMapping("/rank/{type}/user/{userId}")
    public ResponseDto getUserRank(
            @PathVariable String type,
            @PathVariable Long userId
    ) {
        logger.info("获取用户排名: type={}, userId={}", type, userId);
        try {
            int rank = userService.getUserRank(userId, type);
            return ResponseDto.success(Map.of("rank", rank));
        } catch (Exception e) {
            logger.error("获取用户排名失败: {}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
    }
}
