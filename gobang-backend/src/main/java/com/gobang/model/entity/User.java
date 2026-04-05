package com.gobang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体
 * 使用 MyBatis-Plus 注解
 */
@Data
@NoArgsConstructor
@TableName("user")
public class User {

    /**
     * 用户ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 密码（BCrypt 加密）
     */
    @JsonIgnore
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * ELO 积分
     */
    private Integer rating;

    /**
     * 等级
     */
    private Integer level;

    /**
     * 经验值
     */
    private Integer exp;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 最后在线时间
     */
    private LocalDateTime lastOnline;

    /**
     * 状态：0=离线, 1=在线, 2=游戏中, 3=匹配中
     */
    private Integer status;

    /**
     * 构造函数 - 用于注册
     */
    public User(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.avatar = "/default-avatar.png";
        this.rating = 1200;
        this.level = 1;
        this.exp = 0;
        this.status = 0;
    }

    /**
     * 增加经验值
     */
    public void addExp(int exp) {
        this.exp += exp;
        // 每1000经验升1级
        while (this.exp >= 1000) {
            this.exp -= 1000;
            this.level++;
        }
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline() {
        return status != null && status > 0;
    }

    /**
     * 检查用户是否在游戏中
     */
    public boolean isInGame() {
        return status != null && status == 2;
    }

    /**
     * 检查用户是否在匹配中
     */
    public boolean isMatching() {
        return status != null && status == 3;
    }
}
