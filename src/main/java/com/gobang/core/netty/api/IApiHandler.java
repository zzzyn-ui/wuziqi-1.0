package com.gobang.core.netty.api;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * API处理器基础接口
 * 所有API Handler都需要实现此接口
 */
public interface IApiHandler {

    Logger getLogger();

    /**
     * 处理API请求
     * @return true 如果请求已处理，false 如果应该传递给下一个处理器
     */
    boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method);

    /**
     * 获取此Handler处理的路径前缀
     * 例如: /api/friends
     */
    String getPathPrefix();

    /**
     * 发送JSON响应
     */
    default void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status, Map<String, Object> data) {
        try {
            String json = toJsonString(data);
            var response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(json, StandardCharsets.UTF_8)
            );
            response.headers().set("Content-Type", "application/json; charset=UTF-8");
            response.headers().set("Access-Control-Allow-Origin", "*");
            response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.headers().setInt("Content-Length", response.content().readableBytes());
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            getLogger().error("Failed to send JSON response", e);
        }
    }

    /**
     * 将对象转换为JSON字符串
     */
    default String toJsonString(Object obj) {
        if (obj == null) {
            return "null";
        }

        if (obj instanceof Map) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(",");
                first = false;
                json.append("\"").append(entry.getKey()).append("\":");

                Object value = entry.getValue();
                json.append(valueToJson(value));
            }

            json.append("}");
            return json.toString();
        } else if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        } else {
            return "\"" + escapeJson(obj.toString()) + "\"";
        }
    }

    /**
     * 将值转换为JSON格式
     */
    default String valueToJson(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof java.time.LocalDateTime) {
            return "\"" + value.toString() + "\"";
        } else if (value instanceof java.time.LocalDate) {
            return "\"" + value.toString() + "\"";
        } else if (value instanceof java.time.LocalTime) {
            return "\"" + value.toString() + "\"";
        } else if (value instanceof Map) {
            return toJsonString(value);  // 递归处理嵌套Map
        } else if (value instanceof Iterable) {
            // 处理List/Set等集合
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) json.append(",");
                first = false;
                json.append(valueToJson(item));
            }
            json.append("]");
            return json.toString();
        } else {
            // 处理普通对象（如User实体），将其转换为Map
            return toJsonString(objectToMap(value));
        }
    }

    /**
     * 将对象转换为Map（用于JSON序列化）
     */
    default java.util.Map<String, Object> objectToMap(Object obj) {
        if (obj == null) {
            return new java.util.HashMap<>();
        }

        java.util.Map<String, Object> map = new java.util.HashMap<>();

        // 使用反射获取所有字段的值
        try {
            Class<?> clazz = obj.getClass();
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();

            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();

                // 跳过密码字段
                if ("password".equals(fieldName)) {
                    continue;
                }

                Object value = field.get(obj);
                map.put(fieldName, value);
            }
        } catch (Exception e) {
            getLogger().error("Failed to convert object to map", e);
        }

        return map;
    }

    /**
     * 转义JSON字符串
     */
    default String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 从请求中提取Token
     */
    default String extractTokenFromRequest(FullHttpRequest request) {
        // 1. 从Authorization header获取
        String authHeader = request.headers().get("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. 从query参数获取
        String uri = request.uri();
        if (uri.contains("?token=")) {
            int start = uri.indexOf("?token=") + 7;
            int end = uri.indexOf("&", start);
            if (end == -1) end = uri.length();
            return uri.substring(start, end);
        } else if (uri.contains("&token=")) {
            int start = uri.indexOf("&token=") + 7;
            int end = uri.indexOf("&", start);
            if (end == -1) end = uri.length();
            return uri.substring(start, end);
        }

        return null;
    }
}
