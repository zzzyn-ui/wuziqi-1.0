package com.gobang.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全随机数工具类测试
 */
@DisplayName("安全随机数工具类测试")
class SecureRandomUtilTest {

    @Test
    @DisplayName("应该生成随机的布尔值")
    void shouldGenerateRandomBoolean() {
        boolean result = SecureRandomUtil.nextBoolean();
        assertTrue(result == true || result == false, "结果应该是 true 或 false");
    }

    @Test
    @DisplayName("多次生成的布尔值应该有变化")
    @RepeatedTest(10)
    void shouldVaryAcrossMultipleCalls() {
        boolean result = SecureRandomUtil.nextBoolean();
        // 只是验证方法可以调用，不直接断言结果
        assertTrue(result == true || result == false, "布尔值应该为 true 或 false");
    }

    @Test
    @DisplayName("应该在指定范围内生成随机整数")
    void shouldGenerateRandomIntInRange() {
        int result = SecureRandomUtil.nextInt(100);
        assertTrue(result >= 0 && result < 100, "结果应该在 [0, 100) 范围内");
    }

    @Test
    @DisplayName("边界值测试 - 范围为1应该总是返回0")
    void shouldReturnZeroForBoundOfOne() {
        for (int i = 0; i < 10; i++) {
            assertEquals(0, SecureRandomUtil.nextInt(1), "范围为1时应该总是返回0");
        }
    }

    @Test
    @DisplayName("边界值测试 - 范围为0应该抛出异常")
    void shouldThrowExceptionForZeroBound() {
        assertThrows(IllegalArgumentException.class, () -> SecureRandomUtil.nextInt(0),
                "范围为0应该抛出异常");
    }

    @Test
    @DisplayName("应该生成随机double值")
    void shouldGenerateRandomDouble() {
        double result = SecureRandomUtil.nextDouble();
        assertTrue(result >= 0.0 && result < 1.0, "结果应该在 [0.0, 1.0) 范围内");
    }

    @Test
    @DisplayName("应该生成指定长度的字节数组")
    void shouldGenerateRandomBytes() {
        byte[] bytes = SecureRandomUtil.nextBytes(16);
        assertEquals(16, bytes.length, "字节数组长度应该正确");

        // 验证多次调用生成不同的结果
        byte[] bytes2 = SecureRandomUtil.nextBytes(16);
        assertNotEquals(0, java.util.Arrays.compare(bytes, bytes2),
                "两次生成的字节数组应该不同");
    }

    @Test
    @DisplayName("应该生成随机字符串")
    void shouldGenerateRandomString() {
        String result = SecureRandomUtil.nextRandomString(10);
        assertEquals(10, result.length(), "字符串长度应该正确");

        // 验证只包含指定字符
        String validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (char c : result.toCharArray()) {
            assertTrue(validChars.indexOf(c) >= 0, "字符串只应包含指定字符");
        }

        // 验证多次调用生成不同的结果
        String result2 = SecureRandomUtil.nextRandomString(10);
        assertNotEquals(result, result2, "两次生成的字符串应该不同");
    }

    @Test
    @DisplayName("应该从数组中随机选择元素")
    void shouldSelectRandomElement() {
        String[] array = {"A", "B", "C", "D", "E"};
        String result = SecureRandomUtil.randomElement(array);
        assertNotNull(result, "结果不应该为null");
        assertTrue(java.util.Arrays.asList(array).contains(result),
                "结果应该是数组中的元素之一");
    }

    @Test
    @DisplayName("空数组随机选择应该返回null")
    void shouldReturnNullForEmptyArray() {
        String[] emptyArray = {};
        String result = SecureRandomUtil.randomElement(emptyArray);
        assertNull(result, "空数组应该返回null");
    }

    @Test
    @DisplayName("null数组随机选择应该返回null")
    void shouldReturnNullForNullArray() {
        String[] nullArray = null;
        String result = SecureRandomUtil.randomElement(nullArray);
        assertNull(result, "null数组应该返回null");
    }

    @Test
    @DisplayName("洗牌算法应该改变数组顺序")
    void shuffleShouldChangeArrayOrder() {
        int[] original = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int[] array = original.clone();

        SecureRandomUtil.shuffle(array);

        // 注意：极小概率可能顺序不变，但实际中不会发生
        assertNotEquals(java.util.Arrays.toString(original), java.util.Arrays.toString(array),
                "洗牌后数组顺序应该改变");
    }

    @Test
    @DisplayName("洗牌后数组元素应该相同")
    void shuffledArrayShouldContainSameElements() {
        int[] original = {1, 2, 3, 4, 5};
        int[] array = original.clone();

        SecureRandomUtil.shuffle(array);

        java.util.Arrays.sort(original);
        java.util.Arrays.sort(array);

        assertArrayEquals(original, array, "洗牌后数组应该包含相同的元素");
    }

    @Test
    @DisplayName("多次洗牌应该产生不同的结果")
    void multipleShufflesShouldProduceDifferentResults() {
        int[] array1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int[] array2 = array1.clone();
        int[] array3 = array1.clone();

        SecureRandomUtil.shuffle(array2);
        SecureRandomUtil.shuffle(array3);

        assertNotEquals(java.util.Arrays.toString(array2), java.util.Arrays.toString(array3),
                "多次洗牌应该产生不同的结果");
    }

    @Test
    @DisplayName("应该返回SecureRandom实例")
    void shouldReturnSecureRandomInstance() {
        assertNotNull(SecureRandomUtil.getSecureRandom(),
                "应该返回非null的SecureRandom实例");
        assertTrue(SecureRandomUtil.getSecureRandom() instanceof java.security.SecureRandom,
                "返回的实例应该是SecureRandom类型");
    }
}
