package com.gobang.util;

import java.security.SecureRandom;

/**
 * 安全随机数工具类
 * 使用 SecureRandom 替代 Math.random()，提供更强的不可预测性
 */
public final class SecureRandomUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureRandomUtil() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取一个随机的布尔值
     *
     * @return 随机布尔值
     */
    public static boolean nextBoolean() {
        return SECURE_RANDOM.nextBoolean();
    }

    /**
     * 获取一个随机的 int 值
     *
     * @return 随机 int 值
     */
    public static int nextInt() {
        return SECURE_RANDOM.nextInt();
    }

    /**
     * 获取一个 [0, bound) 范围内的随机 int 值
     *
     * @param bound 上界（不包含）
     * @return 随机 int 值
     */
    public static int nextInt(int bound) {
        return SECURE_RANDOM.nextInt(bound);
    }

    /**
     * 获取一个随机的 long 值
     *
     * @return 随机 long 值
     */
    public static long nextLong() {
        return SECURE_RANDOM.nextLong();
    }

    /**
     * 获取一个 [0.0, 1.0) 范围内的随机 double 值
     *
     * @return 随机 double 值
     */
    public static double nextDouble() {
        return SECURE_RANDOM.nextDouble();
    }

    /**
     * 获取一个随机的 float 值
     *
     * @return 随机 float 值
     */
    public static float nextFloat() {
        return SECURE_RANDOM.nextFloat();
    }

    /**
     * 生成指定长度的随机字节数组
     *
     * @param length 字节数组长度
     * @return 随机字节数组
     */
    public static byte[] nextBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * 生成随机字符串（用于生成令牌等）
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    public static String nextRandomString(int length) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 从数组中随机选择一个元素
     *
     * @param array 数组
     * @param <T>   元素类型
     * @return 随机选择的元素
     */
    public static <T> T randomElement(T[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[SECURE_RANDOM.nextInt(array.length)];
    }

    /**
     * 洗牌算法 - 随机打乱数组
     *
     * @param array 数组
     */
    public static void shuffle(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = SECURE_RANDOM.nextInt(i + 1);
            int temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    /**
     * 获取底层的 SecureRandom 实例
     *
     * @return SecureRandom 实例
     */
    public static SecureRandom getSecureRandom() {
        return SECURE_RANDOM;
    }
}
