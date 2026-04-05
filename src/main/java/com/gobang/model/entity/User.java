package com.gobang.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    private String username;

    /**
     * 密码（BCrypt 加密）
     */
    @JsonIgnore
    private String password;

    /**
     * 昵称 - 使用IGNORED策略，即使为null也会插入
     */
    @TableField(insertStrategy = FieldStrategy.IGNORED, updateStrategy = FieldStrategy.IGNORED)
    private String nickname;
    private String email;
    private String avatar;
    private Integer rating;
    private Integer level;
    private Integer exp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastOnline;
    private Integer status;

    /**
     * 创建新用户的构造器
     */
    public User(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        // 如果nickname为空，使用username作为默认昵称
        this.nickname = (nickname != null && !nickname.trim().isEmpty()) ? nickname : username;
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
