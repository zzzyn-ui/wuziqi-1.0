package com.gobang.service;

import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 用户服务
 */
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    /** 默认积分 */
    private static final int DEFAULT_RATING = 1200;

    private final SqlSessionFactory sqlSessionFactory;

    public UserService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 根据ID获取用户
     */
    public User getUserById(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            return userMapper.findById(userId);
        }
    }

    /**
     * 根据用户名获取用户
     */
    public User getUserByUsername(String username) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            return userMapper.findByUsername(username);
        }
    }

    /**
     * 获取用户统计
     */
    public UserStats getUserStats(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserStatsMapper statsMapper = session.getMapper(UserStatsMapper.class);
            UserStats stats = statsMapper.findByUserId(userId);
            if (stats == null) {
                // 创建默认统计
                stats = new UserStats(userId);
                statsMapper.insert(stats);
                session.commit();
            }
            return stats;
        }
    }

    /**
     * 更新用户积分
     */
    public void updateUserRating(Long userId, int newRating, int expGained) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            User user = userMapper.findById(userId);
            if (user != null) {
                user.setRating(newRating);
                user.addExp(expGained);
                userMapper.updateRating(userId, newRating, user.getLevel(), user.getExp());
                session.commit();
                logger.info("Updated rating for user {}: {} (exp: +{})", userId, newRating, expGained);
            }
        }
    }

    /**
     * 获取用户积分
     */
    public int getRating(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            User user = userMapper.findById(userId);
            return user != null ? user.getRating() : DEFAULT_RATING;
        } catch (Exception e) {
            logger.error("Failed to get rating for user {}", userId, e);
            return DEFAULT_RATING;
        }
    }

    /**
     * 更新用户积分（不计算经验值）
     */
    public void updateRating(Long userId, int newRating) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            User user = userMapper.findById(userId);
            if (user != null) {
                userMapper.updateRating(userId, newRating, user.getLevel(), user.getExp());
                session.commit();
                logger.info("Updated rating for user {} to {}", userId, newRating);
            }
        } catch (Exception e) {
            logger.error("Failed to update rating for user {}", userId, e);
        }
    }

    /**
     * 更新用户状态
     */
    public void updateUserStatus(Long userId, int status) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            userMapper.updateStatus(userId, status);
            session.commit();
        }
    }

    /**
     * 获取排行榜
     */
    public List<User> getLeaderboard(int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            return userMapper.getLeaderboard(limit);
        }
    }

    /**
     * 获取在线用户排行榜
     */
    public List<User> getOnlineLeaderboard(int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            return userMapper.getOnlineLeaderboard(limit);
        }
    }

    /**
     * 创建或获取用户统计
     */
    public UserStats getOrCreateStats(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserStatsMapper statsMapper = session.getMapper(UserStatsMapper.class);
            UserStats stats = statsMapper.findByUserId(userId);
            if (stats == null) {
                stats = new UserStats(userId);
                statsMapper.insert(stats);
                session.commit();
            }
            return stats;
        }
    }

    /**
     * 获取在线用户数量
     */
    public int getOnlineUserCount() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            Integer count = userMapper.countOnlineUsers();
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.error("Failed to get online user count", e);
            return 0;
        }
    }
}
