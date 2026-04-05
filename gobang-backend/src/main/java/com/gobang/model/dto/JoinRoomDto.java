package com.gobang.model.dto;

import lombok.Data;

/**
 * 加入房间 DTO
 */
@Data
public class JoinRoomDto {
    private String roomId;
    private String password;
}
