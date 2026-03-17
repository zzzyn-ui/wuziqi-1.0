package com.gobang.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 敏感词过滤器测试类
 */
@DisplayName("敏感词过滤器测试")
class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter();
        // 添加测试用敏感词
        filter.addSensitiveWord("测试");
        filter.addSensitiveWord("违规");
    }

    @Test
    @DisplayName("应该检测到包含敏感词")
    void shouldDetectSensitiveWord() {
        assertTrue(filter.contains("这是一个测试"), "应该检测到敏感词");
        assertTrue(filter.contains("违规内容"), "应该检测到敏感词");
    }

    @Test
    @DisplayName("应该检测到不包含敏感词")
    void shouldNotContainSensitiveWord() {
        assertFalse(filter.contains("正常内容"), "正常内容不应该包含敏感词");
        assertFalse(filter.contains(""), "空字符串不应该包含敏感词");
    }

    @Test
    @DisplayName("应该过滤敏感词")
    void shouldFilterSensitiveWord() {
        String result = filter.filter("这是一个测试");
        assertEquals("这是一个***", result, "敏感词应该被替换为***");
    }

    @Test
    @DisplayName("应该过滤多个敏感词")
    void shouldFilterMultipleSensitiveWords() {
        String result = filter.filter("这是一个测试，也是违规内容");
        assertEquals("这是一个***，也是***内容", result, "多个敏感词都应该被过滤");
    }

    @Test
    @DisplayName("应该获取敏感词列表")
    void shouldGetSensitiveWords() {
        var words = filter.getSensitiveWords("这是一个测试，还有违规");
        assertTrue(words.contains("测试"), "应该获取到敏感词");
        assertTrue(words.contains("违规"), "应该获取到敏感词");
        assertEquals(2, words.size(), "应该获取到2个敏感词");
    }

    @Test
    @DisplayName("空字符串处理应该正确")
    void emptyStringShouldHandleCorrectly() {
        assertFalse(filter.contains(""), "空字符串不应该包含敏感词");
        assertEquals("", filter.filter(""), "过滤空字符串应该返回空字符串");
        assertTrue(filter.getSensitiveWords("").isEmpty(), "空字符串的敏感词列表应该为空");
    }

    @Test
    @DisplayName("null输入应该安全处理")
    void nullInputShouldHandleSafely() {
        assertFalse(filter.contains(null), "null不应该包含敏感词");
        assertNull(filter.filter(null), "过滤null应该返回null");
        assertTrue(filter.getSensitiveWords(null).isEmpty(), "null的敏感词列表应该为空");
    }

    @Test
    @DisplayName("应该正确处理混合大小写")
    void shouldHandleMixedCase() {
        filter.addSensitiveWord("ABC");
        assertTrue(filter.contains("abc"), "应该检测到小写敏感词");
        assertTrue(filter.contains("ABC"), "应该检测到大写敏感词");
        assertTrue(filter.contains("AbC"), "应该检测到混合大小写敏感词");
    }
}
