package com.gobang.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求DTO
 */
@Data
@NoArgsConstructor
public class LoginDto {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;
}
