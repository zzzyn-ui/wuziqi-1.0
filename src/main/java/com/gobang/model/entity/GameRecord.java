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
     * 对局记录ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Long blackPlayerId;
    private Long whitePlayerId;
    private Long winnerId;
    private Integer winColor;
    private Integer endReason;
    private Integer moveCount;
    private Integer duration;
    private Integer blackRatingBefore;
    private Integer blackRatingAfter;
    private Integer blackRatingChange;
    private Integer whiteRatingBefore;
    private Integer whiteRatingAfter;
    private Integer whiteRatingChange;
    private String boardState;
    private String moves;
    private String gameMode; // pve(人机), pvp_online(在线对战), pvp_local(本地对战)
    private LocalDateTime createdAt;
}
