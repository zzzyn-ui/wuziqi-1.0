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
        this.rating = 700;
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

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getExp() { return exp; }
    public void setExp(Integer exp) { this.exp = exp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastOnline() { return lastOnline; }
    public void setLastOnline(LocalDateTime lastOnline) { this.lastOnline = lastOnline; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
