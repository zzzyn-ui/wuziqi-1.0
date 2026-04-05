package com.gobang.service;

import com.gobang.model.dto.LoginDto;
import com.gobang.model.dto.RegisterDto;
import com.gobang.model.entity.User;

/**
 * 用户服务接口
 * 定义登录、注册、更新积分等方法
 */
public interface UserService {

    /**
     * 用户登录
     *
     * @param loginDto 登录请求DTO
     * @return JWT Token
     * @throws RuntimeException 用户不存在或密码错误
     */
    String login(LoginDto loginDto);

    /**
     * 用户注册
     *
     * @param registerDto 注册请求DTO
     * @throws RuntimeException 用户名已存在
     */
    void register(RegisterDto registerDto);

    /**
     * 根据ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息，不存在返回null
     */
    User getUserById(Long userId);

    /**
     * 根据Token获取用户信息
     *
     * @param token JWT Token
     * @return 用户信息，token无效返回null
     */
    User getUserByToken(String token);

    /**
     * 更新用户积分
     *
     * @param userId 用户ID
     * @param newRating 新积分
     */
    void updateUserRating(Long userId, int newRating);

    /**
     * 增加用户经验值
     *
     * @param userId 用户ID
     * @param exp 经验值
     */
    void addUserExp(Long userId, int exp);

    /**
     * 更新用户状态
     *
     * @param userId 用户ID
     * @param status 状态：0=离线, 1=在线, 2=游戏中, 3=匹配中
     */
    void updateUserStatus(Long userId, int status);
}
