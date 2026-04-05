package com.gobang.model.dto;

import lombok.Data;

/**
 * 落子请求 DTO
 */
@Data
public class GameMoveDto {
    private String roomId;
    private int x;
    private int y;
}
