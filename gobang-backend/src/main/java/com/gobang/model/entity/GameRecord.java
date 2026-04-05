package com.gobang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对局记录实体
 * 使用 MyBatis-Plus 注解
 */
@Data
@NoArgsConstructor
@TableName("game_record")
public class GameRecord {

    /**
     * 对局ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 黑方玩家ID
     */
    private Long blackPlayerId;

    /**
     * 白方玩家ID
     */
    private Long whitePlayerId;

    /**
     * 获胜者ID
     */
    private Long winnerId;

    /**
     * 获胜方颜色：1=黑, 2=白
     */
    private Integer winColor;

    /**
     * 结束原因：0=胜利, 1=失败, 2=平局, 3=认输, 4=超时
     */
    private Integer endReason;

    /**
     * 总落子数
     */
    private Integer moveCount;

    /**
     * 对局时长（秒）
     */
    private Integer duration;

    /**
     * 黑方积分变化前
     */
    private Integer blackRatingBefore;

    /**
     * 黑方积分变化后
     */
    private Integer blackRatingAfter;

    /**
     * 黑方积分变化
     */
    private Integer blackRatingChange;

    /**
     * 白方积分变化前
     */
    private Integer whiteRatingBefore;

    /**
     * 白方积分变化后
     */
    private Integer whiteRatingAfter;

    /**
     * 白方积分变化
     */
    private Integer whiteRatingChange;

    /**
     * 最终棋盘状态（压缩）
     */
    private String boardState;

    /**
     * 所有落子记录（JSON数组）
     */
    private String moves;

    /**
     * 游戏模式：pve(人机), pvp_online(在线对战), pvp_local(本地对战)
     */
    private String gameMode;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    // ==================== 辅助方法 ====================

    /**
     * 检查是否有人机参与
     */
    public boolean isPvE() {
        return "pve".equals(gameMode);
    }

    /**
     * 检查是否是在线对战
     */
    public boolean isOnlinePvP() {
        return "pvp_online".equals(gameMode);
    }

    /**
     * 检查是否是本地对战
     */
    public boolean isLocalPvP() {
        return "pvp_local".equals(gameMode);
    }

    /**
     * 获取指定玩家的积分变化
     */
    public Integer getRatingChange(Long playerId) {
        if (playerId.equals(blackPlayerId)) {
            return blackRatingChange;
        } else if (playerId.equals(whitePlayerId)) {
            return whiteRatingChange;
        }
        return 0;
    }

    /**
     * 检查指定玩家是否获胜
     */
    public boolean isPlayerWin(Long playerId) {
        return playerId != null && playerId.equals(winnerId);
    }
}
