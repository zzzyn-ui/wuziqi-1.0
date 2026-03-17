package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.mapper.UserActivityLogMapper;
import com.gobang.model.entity.UserActivityLog;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户活动日志服务
 */
public class ActivityLogService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityLogService.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final ObjectMapper objectMapper;

    public ActivityLogService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 记录用户活动
     */
    public void logActivity(Long userId, String activityType) {
        logActivity(userId, activityType, null, null, null);
    }

    /**
     * 记录用户活动（带附加数据）
     */
    public void logActivity(Long userId, String activityType, Map<String, Object> data) {
        try {
            String jsonData = data != null ? objectMapper.writeValueAsString(data) : null;
            logActivity(userId, activityType, null, null, jsonData);
        } catch (Exception e) {
            logger.error("序列化活动数据失败", e);
            logActivity(userId, activityType, null, null, null);
        }
    }

    /**
     * 记录用户活动（完整信息）
     */
    public void logActivity(Long userId, String activityType, String ipAddress, String userAgent, String activityData) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserActivityLogMapper mapper = session.getMapper(UserActivityLogMapper.class);

            UserActivityLog log = new UserActivityLog(userId, activityType);
            log.setIpAddress(ipAddress);
            log.setUserAgent(userAgent);
            log.setActivityData(activityData);

            mapper.insert(log);
            logger.debug("记录用户活动: userId={}, type={}", userId, activityType);
        }
    }

    /**
     * 获取用户最近的活动日志
     */
    public List<UserActivityLog> getUserRecentActivities(Long userId, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserActivityLogMapper mapper = session.getMapper(UserActivityLogMapper.class);
            return mapper.findByUserId(userId, limit);
        }
    }

    /**
     * 获取用户的最后登录记录
     */
    public UserActivityLog getLastLogin(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserActivityLogMapper mapper = session.getMapper(UserActivityLogMapper.class);
            return mapper.findLastLogin(userId);
        }
    }

    /**
     * 统计用户某类活动次数
     */
    public int countUserActivity(Long userId, String activityType) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserActivityLogMapper mapper = session.getMapper(UserActivityLogMapper.class);
            return mapper.countByUserAndActivity(userId, activityType);
        }
    }

    /**
     * 清理过期的日志
     */
    public int cleanOldLogs(int daysToKeep) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserActivityLogMapper mapper = session.getMapper(UserActivityLogMapper.class);
            LocalDateTime beforeDate = LocalDateTime.now().minusDays(daysToKeep);
            int deleted = mapper.deleteBeforeDate(beforeDate);
            logger.info("清理过期日志: 删除{}条，保留{}天", deleted, daysToKeep);
            return deleted;
        }
    }

    /**
     * 快捷方法：记录登录
     */
    public void logLogin(Long userId, String ipAddress, String userAgent) {
        logActivity(userId, UserActivityLog.TYPE_LOGIN, ipAddress, userAgent, null);
    }

    /**
     * 快捷方法：记录登出
     */
    public void logLogout(Long userId) {
        logActivity(userId, UserActivityLog.TYPE_LOGOUT);
    }

    /**
     * 快捷方法：记录注册
     */
    public void logRegister(Long userId, String ipAddress) {
        logActivity(userId, UserActivityLog.TYPE_REGISTER, ipAddress, null, null);
    }

    /**
     * 快捷方法：记录匹配开始
     */
    public void logMatchStart(Long userId, String mode) {
        logActivity(userId, UserActivityLog.TYPE_MATCH_START, Map.of("mode", mode));
    }

    /**
     * 快捷方法：记录匹配成功
     */
    public void logMatchSuccess(Long userId, String roomId, Long opponentId) {
        logActivity(userId, UserActivityLog.TYPE_MATCH_SUCCESS, Map.of(
            "roomId", roomId,
            "opponentId", opponentId
        ));
    }

    /**
     * 快捷方法：记录游戏开始
     */
    public void logGameStart(Long userId, String roomId) {
        logActivity(userId, UserActivityLog.TYPE_GAME_START, Map.of("roomId", roomId));
    }

    /**
     * 快捷方法：记录游戏结束
     */
    public void logGameEnd(Long userId, String roomId, Boolean isWin) {
        logActivity(userId, UserActivityLog.TYPE_GAME_END, Map.of(
            "roomId", roomId,
            "isWin", isWin
        ));
    }
}
