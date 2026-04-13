package com.gobang.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加入房间DTO
 */
@Data
@NoArgsConstructor
public class JoinRoomDto {

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 房间密码（可选）
     */
    private String password;
}
