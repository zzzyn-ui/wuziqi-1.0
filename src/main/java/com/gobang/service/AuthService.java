package com.gobang.service;

import com.gobang.mapper.UserMapper;
import com.gobang.model.entity.User;
import com.gobang.util.JwtUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * 认证服务
 */
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final int BCRYPT_WORK_FACTOR = 12; // BCrypt 工作因子

    private final SqlSessionFactory sqlSessionFactory;
    private final JwtUtil jwtUtil;

    public AuthService(SqlSessionFactory sqlSessionFactory, JwtUtil jwtUtil) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 密码
     * @param nickname 昵称
     * @return 注册的用户，失败返回null
     */
    public User register(String username, String password, String nickname) {
        logger.info("=== REGISTER START ===");
        logger.info("Register request - username: {}, nickname: {}", username, nickname);

        try (SqlSession session = sqlSessionFactory.openSession()) {
            logger.info("SqlSession created successfully");

            UserMapper userMapper = session.getMapper(UserMapper.class);
            logger.info("UserMapper obtained: {}", userMapper);

            // 检查用户名是否已存在
            User existing = userMapper.findByUsername(username);
            if (existing != null) {
                logger.warn("Registration failed: username {} already exists", username);
                return null;
            }
            logger.info("Username check passed - username {} is available", username);

            // 使用 BCrypt 加密密码
            String hashedPassword = BCrypt.withDefaults().hashToString(BCRYPT_WORK_FACTOR, password.toCharArray());
            logger.info("Password hashed successfully");

            // 创建新用户
            User user = new User(username, hashedPassword, nickname);
            user.setStatus(0);
            user.setCreatedAt(LocalDateTime.now());
            logger.info("User object created: username={}, nickname={}, status={}",
                user.getUsername(), user.getNickname(), user.getStatus());

            int result = userMapper.insert(user);
            logger.info("Insert executed, result: {}, generated ID: {}", result, user.getId());

            session.commit();
            logger.info("Transaction committed");

            if (result > 0) {
                logger.info("User registered successfully: {} with ID: {}", username, user.getId());
                return user;
            }

            logger.warn("Insert returned 0, registration failed");
            return null;
        } catch (Exception e) {
            logger.error("Registration failed with exception for username: {}", username, e);
            return null;
        } finally {
            logger.info("=== REGISTER END ===");
        }
    }

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录的用户，失败返回null
     */
    public User login(String username, String password) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserMapper userMapper = session.getMapper(UserMapper.class);

            User user = userMapper.findByUsername(username);
            if (user == null) {
                logger.warn("Login failed: user {} not found", username);
                return null;
            }

            // 使用 BCrypt 验证密码
            try {
                BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPassword());
                if (!result.verified) {
                    logger.warn("Login failed: invalid password for user {}", username);
                    return null;
                }
            } catch (Exception e) {
                logger.error("Password verification error", e);
                return null;
            }

            // 更新最后在线时间
            user.setLastOnline(LocalDateTime.now());
            userMapper.updateLastOnline(user.getId(), user.getLastOnline());
            session.commit();

            logger.info("User logged in: {}", username);
            return user;
        }
    }

    /**
     * 验证JWT令牌
     *
     * @param token JWT令牌
     * @return 用户ID，验证失败返回null
     */
    public Long validateToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return null;
        }
        return jwtUtil.extractUserId(token);
    }

    /**
     * 生成JWT令牌
     *
     * @param userId 用户ID
     * @return JWT令牌
     */
    public String generateToken(Long userId) {
        return jwtUtil.generateToken(userId);
    }

    /**
     * 验证密码（用于密码修改等功能）
     *
     * @param password       明文密码
     * @param hashedPassword 加密后的密码
     * @return 是否匹配
     */
    public boolean verifyPassword(String password, String hashedPassword) {
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword);
            return result.verified;
        } catch (Exception e) {
            logger.error("Password verification error", e);
            return false;
        }
    }

    /**
     * 加密密码（用于密码修改等功能）
     *
     * @param password 明文密码
     * @return 加密后的密码
     */
    public String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(BCRYPT_WORK_FACTOR, password.toCharArray());
    }
}
