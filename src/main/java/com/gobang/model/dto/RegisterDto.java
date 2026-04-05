package com.gobang.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求DTO
 */
@Data
@NoArgsConstructor
public class RegisterDto {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;
}
