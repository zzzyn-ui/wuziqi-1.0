package com.gobang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 安全过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（使用JWT不需要）
            .csrf(csrf -> csrf.disable())

            // 配置 CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 配置会话管理为无状态
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // 放行 WebSocket 端点（重要：必须在其他规则之前）
                .requestMatchers("/ws").permitAll()
                .requestMatchers("/ws/**").permitAll()
                // 放行SockJS相关的端点
                .requestMatchers("/ws/**").permitAll()
                // 放行登录和注册
                .requestMatchers("/api/auth/**").permitAll()
                // 放行排行榜、用户统计、对局记录等公开API
                .requestMatchers("/api/rank/**").permitAll()
                .requestMatchers("/api/user/stats").permitAll()
                .requestMatchers("/api/records/**").permitAll()
                .requestMatchers("/api/friend/**").permitAll()
                // 放行静态资源
                .requestMatchers("/static/**", "/assets/**", "/*.html", "/*.js", "/*.css").permitAll()
                // 放行测试页面
                .requestMatchers("/websocket-test.html").permitAll()
                // 其他请求需要认证
                .anyRequest().authenticated()
            )

            // 禁用默认登录页（使用自定义登录）
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * CORS 配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许前端开发服务器的来源
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
