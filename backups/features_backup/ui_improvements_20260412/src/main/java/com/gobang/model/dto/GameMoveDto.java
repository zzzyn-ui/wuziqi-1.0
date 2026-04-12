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

    // Getters and Setters
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public Integer getX() { return x; }
    public void setX(Integer x) { this.x = x; }

    public Integer getY() { return y; }
    public void setY(Integer y) { this.y = y; }
}
