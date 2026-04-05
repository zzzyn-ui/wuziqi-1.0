package com.gobang.model.dto;

import lombok.Data;

/**
 * 注册请求 DTO
 */
@Data
public class RegisterDto {
    private String username;
    private String password;
    private String nickname;
    private String email;
}
