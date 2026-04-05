package com.gobang.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游戏落子DTO
 */
@Data
@NoArgsConstructor
public class GameMoveDto {

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * X坐标
     */
    private Integer x;

    /**
     * Y坐标
     */
    private Integer y;
}
