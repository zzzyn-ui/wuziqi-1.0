package com.gobang.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gobang.mapper.UserMapper;
import com.gobang.model.dto.LoginDto;
import com.gobang.model.dto.RegisterDto;
import com.gobang.model.entity.User;
import com.gobang.util.JwtUtil;
import at.favre.lib.crypto.bcrypt.BCrypt;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * 用户登录
     */
    public String login(LoginDto loginDto) {
        // 查询用户
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
    public void register(RegisterDto registerDto) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, registerDto.getUsername());
        Long count = userMapper.selectCount(wrapper);

        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 创建新用户
        User user = new User(
            registerDto.getUsername(),
            BCrypt.withDefaults().hashToString(12, registerDto.getPassword().toCharArray()),
            registerDto.getNickname()
        );

        if (registerDto.getEmail() != null && !registerDto.getEmail().isEmpty()) {
            user.setEmail(registerDto.getEmail());
        }

        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);
        logger.info("用户注册成功: {}", registerDto.getUsername());
    }

    /**
     * 根据 ID 获取用户信息
     */
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 根据 Token 获取用户信息
     */
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
    public void updateUserRating(Long userId, int newRating) {
        User user = new User();
        user.setId(userId);
        user.setRating(newRating);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 更新用户昵称
     */
    public void updateUserNickname(Long userId, String nickname) {
        User user = new User();
        user.setId(userId);
        user.setNickname(nickname);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 增加用户经验值
     */
    public void addUserExp(Long userId, int exp) {
        User user = getUserById(userId);
        if (user != null) {
            user.addExp(exp);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    /**
     * 获取用户统计信息
     */
    public Map<String, Object> getUserStats(Long userId) {
        User user = getUserById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGames", 0);
        stats.put("wins", 0);
        stats.put("losses", 0);
        stats.put("draws", 0);
        stats.put("winRate", 0);
        stats.put("level", user.getLevel());
        stats.put("exp", user.getExp());
        stats.put("rating", user.getRating());

        return stats;
    }

    /**
     * 搜索用户
     */
    public List<Map<String, Object>> searchUsers(String query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(User::getUsername, query)
               .or()
               .like(User::getNickname, query);
        wrapper.last("LIMIT 20");

        List<User> users = userMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("nickname", user.getNickname());
            userData.put("level", user.getLevel());
            userData.put("rating", user.getRating());
            userData.put("online", isUserOnline(user));
            result.add(userData);
        }

        return result;
    }

    /**
     * 获取排行榜
     */
    public List<Map<String, Object>> getRankList(String type, int limit) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getRating);
        wrapper.last("LIMIT " + limit);

        List<User> users = userMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();

        int rank = 1;
        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("nickname", user.getNickname());
            userData.put("level", user.getLevel());
            userData.put("rating", user.getRating());
            userData.put("totalGames", 0);
            userData.put("winRate", 0);
            userData.put("online", isUserOnline(user));
            userData.put("rank", rank++);

            result.add(userData);
        }

        return result;
    }

    /**
     * 获取用户排名
     */
    public int getUserRank(Long userId, String type) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getRating);

        List<User> users = userMapper.selectList(wrapper);
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId().equals(userId)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 获取用户对局记录
     */
    public List<Map<String, Object>> getUserRecords(Long userId, String filter, int limit) {
        // TODO: 实现从数据库获取对局记录
        List<Map<String, Object>> records = new ArrayList<>();

        // 模拟数据
        Map<String, Object> record1 = new HashMap<>();
        record1.put("id", "1");
        record1.put("opponent", "高手玩家");
        record1.put("result", "win");
        record1.put("mode", "ranked");
        record1.put("ratingChange", 25);
        record1.put("date", "2026-04-05 12:30");
        records.add(record1);

        return records;
    }

    /**
     * 获取对局详情
     */
    public Map<String, Object> getGameRecord(Long userId, String gameId) {
        // TODO: 实现从数据库获取对局详情
        Map<String, Object> record = new HashMap<>();
        record.put("id", gameId);
        record.put("moves", new ArrayList<>());
        return record;
    }

    /**
     * 获取复盘数据
     */
    public Map<String, Object> getReplayData(String gameId) {
        // TODO: 实现从数据库获取复盘数据
        Map<String, Object> replay = new HashMap<>();
        replay.put("gameId", gameId);
        replay.put("moves", new ArrayList<>());
        return replay;
    }

    /**
     * 获取好友列表
     */
    public List<Map<String, Object>> getFriendList(Long userId) {
        // TODO: 实现从数据库获取好友列表
        List<Map<String, Object>> friends = new ArrayList<>();
        return friends;
    }

    /**
     * 发送好友请求
     */
    public void sendFriendRequest(Long userId, Long targetUserId, String message) {
        // TODO: 实现好友请求功能
        logger.info("发送好友请求: {} -> {}, message={}", userId, targetUserId, message);
    }

    /**
     * 获取好友请求列表
     */
    public List<Map<String, Object>> getFriendRequests(Long userId) {
        // TODO: 实现从数据库获取好友请求
        List<Map<String, Object>> requests = new ArrayList<>();
        return requests;
    }

    /**
     * 处理好友请求
     */
    public void handleFriendRequest(Long requestId, Long userId) {
        // TODO: 实现处理好友请求
        logger.info("处理好友请求: requestId={}, userId={}", requestId, userId);
    }

    /**
     * 删除好友
     */
    public void deleteFriend(Long userId, Long friendId) {
        // TODO: 实现删除好友功能
        logger.info("删除好友: {} -> {}", userId, friendId);
    }

    /**
     * 获取残局列表
     */
    public List<Map<String, Object>> getPuzzleList(String difficulty) {
        // TODO: 实现从数据库获取残局列表
        List<Map<String, Object>> puzzles = new ArrayList<>();

        // 模拟数据
        Map<String, Object> puzzle1 = new HashMap<>();
        puzzle1.put("id", 1);
        puzzle1.put("name", "初级残局 " + difficulty + "1");
        puzzle1.put("difficulty", difficulty);
        puzzle1.put("moves", 5);
        puzzle1.put("description", "五子连珠基础练习");
        puzzles.add(puzzle1);

        return puzzles;
    }

    /**
     * 获取残局详情
     */
    public Map<String, Object> getPuzzleDetail(Long puzzleId) {
        // TODO: 实现从数据库获取残局详情
        Map<String, Object> puzzle = new HashMap<>();
        puzzle.put("id", puzzleId);
        puzzle.put("name", "残局名称");
        puzzle.put("difficulty", "beginner");
        puzzle.put("board", new int[15][15]);
        puzzle.put("solution", new ArrayList<>());
        return puzzle;
    }

    /**
     * 检查残局答案
     */
    public boolean checkPuzzleAnswer(Long puzzleId, List<int[]> moves) {
        // TODO: 实现残局答案验证
        return true;
    }

    /**
     * 记录残局完成
     */
    public void recordPuzzleCompletion(Long userId, Long puzzleId, boolean success) {
        // TODO: 实现记录残局完成
        logger.info("记录残局完成: userId={}, puzzleId={}, success={}", userId, puzzleId, success);
    }

    /**
     * 获取用户残局统计
     */
    public Map<String, Object> getUserPuzzleStats(Long userId) {
        // TODO: 实现从数据库获取残局统计
        Map<String, Object> stats = new HashMap<>();
        stats.put("completed", 0);
        stats.put("total", 0);
        stats.put("accuracy", 0);
        return stats;
    }

    /**
     * 检查用户是否在线
     */
    private boolean isUserOnline(User user) {
        if (user.getLastOnline() == null) {
            return false;
        }
        // 5分钟内有活动视为在线
        return user.getLastOnline().isAfter(LocalDateTime.now().minusMinutes(5));
    }
}
