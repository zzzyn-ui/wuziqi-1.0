package com.gobang.util;

/**
 * 密码强度验证工具
 */
public class PasswordValidator {

    /** 最小密码长度 */
    private static final int MIN_LENGTH = 6;

    /** 建议的密码长度 */
    private static final int RECOMMENDED_LENGTH = 8;

    /**
     * 验证密码强度
     * @param password 密码
     * @return 验证结果，null表示验证通过，否则返回错误消息
     */
    public static String validate(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }

        if (password.length() < MIN_LENGTH) {
            return "密码长度至少" + MIN_LENGTH + "位";
        }

        if (password.length() > 50) {
            return "密码长度不能超过50位";
        }

        // 检查密码复杂度（建议但不强制）
        int score = calculateStrength(password);
        if (score < 2) {
            return "密码过于简单，建议包含大小写字母、数字或特殊字符";
        }

        // 检查常见弱密码
        if (isWeakPassword(password)) {
            return "密码过于简单，请使用更复杂的密码";
        }

        return null; // 验证通过
    }

    /**
     * 计算密码强度（0-4）
     * 0: 很弱
     * 1: 弱
     * 2: 中等
     * 3: 强
     * 4: 很强
     */
    public static int calculateStrength(String password) {
        int score = 0;

        // 长度加分
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;

        // 包含小写字母
        if (password.matches(".*[a-z].*")) score++;

        // 包含大写字母
        if (password.matches(".*[A-Z].*")) score++;

        // 包含数字
        if (password.matches(".*\\d.*")) score++;

        // 包含特殊字符
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;

        return Math.min(score, 4);
    }

    /**
     * 获取密码强度描述
     */
    public static String getStrengthDescription(int score) {
        switch (score) {
            case 0: return "很弱";
            case 1: return "弱";
            case 2: return "中等";
            case 3: return "强";
            case 4: return "很强";
            default: return "未知";
        }
    }

    /**
     * 检查是否为弱密码
     */
    private static boolean isWeakPassword(String password) {
        // 常见弱密码列表
        String[] weakPasswords = {
            "123456", "password", "123456789", "12345678",
            "111111", "abc123", "123123", "admin", "qwerty",
            "654321", "123321", "password123", "admin123",
            "root", "test", "guest", "user", "pass"
        };

        String lowerPassword = password.toLowerCase();
        for (String weak : weakPasswords) {
            if (lowerPassword.equals(weak) || lowerPassword.contains(weak)) {
                return true;
            }
        }

        // 纯数字或纯字母
        if (password.matches("^\\d+$") || password.matches("^[a-zA-Z]+$")) {
            return true;
        }

        // 重复字符（如：aaaaaa, 111111）
        if (password.matches("(.)\\1{5,}")) {
            return true;
        }

        return false;
    }

    /**
     * 检查密码是否符合高强度要求（用于强制要求）
     */
    public static boolean isStrong(String password) {
        return password.length() >= RECOMMENDED_LENGTH
            && password.matches(".*[a-z].*")      // 小写字母
            && password.matches(".*[A-Z].*")      // 大写字母
            && password.matches(".*\\d.*")        // 数字
            && password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*"); // 特殊字符
    }
}
