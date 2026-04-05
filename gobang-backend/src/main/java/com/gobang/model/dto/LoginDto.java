package com.gobang.model.dto;

import lombok.Data;

/**
 * 登录请求 DTO
 */
@Data
public class LoginDto {
    private String username;
    private String password;
}
