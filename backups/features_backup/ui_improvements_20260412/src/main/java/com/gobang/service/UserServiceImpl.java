package com.gobang.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gobang.mapper.UserMapper;
import com.gobang.mapper.UserStatsMapper;
import com.gobang.model.dto.LoginDto;
import com.gobang.model.dto.RegisterDto;
import com.gobang.model.entity.User;
import com.gobang.model.entity.UserStats;
import com.gobang.util.JwtUtil;
import at.favre.lib.crypto.bcrypt.BCrypt;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 * 使用 BCrypt 加密密码
 * 使用 JwtUtil 生成 JWT token
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final UserStatsMapper userStatsMapper;
    private final JwtUtil jwtUtil;

    /**
     * 用户登录
     */
    @Override
    public String login(LoginDto loginDto) {
        // 查询用户 - 只使用 username 验证
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginDto.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证密码
        if (!BCrypt.verifyer().verify(loginDto.getPassword().toCharArray(), user.getPassword()).verified) {
            throw new RuntimeException("密码错误");
        }

        // 更新最后在线时间
        user.setLastOnline(LocalDateTime.now());
        userMapper.updateById(user);

        // 生成 JWT Token
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    /**
     * 用户注册
     */
    @Override
    @Transactional
    public void register(RegisterDto registerDto) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, registerDto.getUsername());
        Long count = userMapper.selectCount(wrapper);

        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 处理昵称：如果为空，使用用户名作为默认昵称
        String nickname = registerDto.getNickname();
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = registerDto.getUsername();
        }

        // 创建新用户
        User user = new User(
            registerDto.getUsername(),
            BCrypt.withDefaults().hashToString(12, registerDto.getPassword().toCharArray()),
            nickname
        );

        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);

        // 创建用户统计记录
        UserStats stats = new UserStats(user.getId());
        userStatsMapper.insertStats(stats);

        logger.info("用户注册成功: {} (昵称: {}), ID: {}", registerDto.getUsername(), nickname, user.getId());
    }

    /**
     * 根据 ID 获取用户信息
     */
    @Override
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 根据 Token 获取用户信息
     */
    @Override
    public User getUserByToken(String token) {
        Long userId = jwtUtil.extractUserId(token);
        if (userId == null) {
            return null;
        }
        return getUserById(userId);
    }

    /**
     * 更新用户状态
     */
    @Override
    public void updateUserStatus(Long userId, int status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 更新用户积分
     */
    @Override
    public void updateUserRating(Long userId, int newRating) {
        // 先获取完整用户信息，避免将其他字段更新为 null
        User user = getUserById(userId);
        if (user != null) {
            user.setRating(newRating);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        } else {
            logger.error("更新积分失败：找不到用户，userId={}", userId);
        }
    }

    /**
     * 增加用户经验值
     */
    @Override
    public void addUserExp(Long userId, int exp) {
        User user = getUserById(userId);
        if (user != null) {
            user.addExp(exp);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    /**
     * 更新用户最后在线时间
     */
    @Override
    public void updateLastOnline(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setLastOnline(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
