package com.gobang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 五子棋游戏服务器 - Spring Boot 主类
 */
@SpringBootApplication
@EnableScheduling
public class GobangApplication {

    public static void main(String[] args) {
        SpringApplication.run(GobangApplication.class, args);
        System.out.println("========================================");
        System.out.println("  五子棋游戏服务器启动成功！");
        System.out.println("  WebSocket: ws://localhost:8080/ws");
        System.out.println("  REST API: http://localhost:8080/api");
        System.out.println("========================================");
    }
}
