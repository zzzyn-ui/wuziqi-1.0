package com.gobang.core.rating;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ELO积分计算器测试类
 */
@DisplayName("ELO积分计算器测试")
class ELOCalculatorTest {

    @Test
    @DisplayName("同分对局获胜应该获得16分")
    void sameRatingWinShouldGain16Points() {
        int[] result = ELOCalculator.calculateRatingChange(1500, 1500);
        assertEquals(1516, result[0], "获胜方应该获得16分");
        assertEquals(1484, result[1], "失败方应该失去16分");
    }

    @Test
    @DisplayName("高分战胜低分应该获得较少积分")
    void highBeatingLowGainsLess() {
        int[] result = ELOCalculator.calculateRatingChange(1800, 1200);
        assertTrue(result[2] < 16, "高分战胜低分获得的积分应该少于16");
        assertTrue(result[2] > 0, "获胜方积分应该增加");
        assertTrue(result[3] < 0, "失败方积分应该减少");
    }

    @Test
    @DisplayName("低分战胜高分应该获得较多积分")
    void lowBeatingHighGainsMore() {
        int[] result = ELOCalculator.calculateRatingChange(1200, 1800);
        assertTrue(result[2] > 16, "低分战胜高分获得的积分应该多于16");
        assertTrue(result[2] < 32, "积分增加应该有上限");
        assertTrue(result[3] < 0, "失败方积分应该减少");
    }

    @Test
    @DisplayName("平局时低分方应该获得积分")
    void drawWithLowerRatingGainsPoints() {
        int[] result = ELOCalculator.calculateDrawRatingChange(1400, 1600);
        assertTrue(result[2] > 0, "平局时低分方应该获得积分");
        assertTrue(result[3] < 0, "平局时高分方应该失去积分");
    }

    @Test
    @DisplayName("平局时同分双方积分不变")
    void drawWithSameRatingNoChange() {
        int[] result = ELOCalculator.calculateDrawRatingChange(1500, 1500);
        assertEquals(1500, result[0], "平局时同分双方积分应该不变");
        assertEquals(1500, result[1], "平局时同分双方积分应该不变");
    }

    @Test
    @DisplayName("预期得分应该在0到1之间")
    void expectedScoreShouldBeBetween0And1() {
        double expected1 = ELOCalculator.calculateExpectedScore(1500, 1500);
        assertEquals(0.5, expected1, 0.001, "同分预期得分应该是0.5");

        double expected2 = ELOCalculator.calculateExpectedScore(2000, 1000);
        assertTrue(expected2 > 0.5 && expected2 < 1.0, "高分方预期得分应该大于0.5");

        double expected3 = ELOCalculator.calculateExpectedScore(1000, 2000);
        assertTrue(expected3 > 0 && expected3 < 0.5, "低分方预期得分应该小于0.5");
    }

    @Test
    @DisplayName("等级计算应该正确")
    void levelCalculationShouldBeCorrect() {
        assertEquals(1, ELOCalculator.calculateLevel(1199), "1200以下应该是1级");
        assertEquals(2, ELOCalculator.calculateLevel(1200), "1200应该是2级");
        assertEquals(2, ELOCalculator.calculateLevel(1399), "1399应该是2级");
        assertEquals(3, ELOCalculator.calculateLevel(1400), "1400应该是3级");
        assertEquals(3, ELOCalculator.calculateLevel(1599), "1599应该是3级");
        assertEquals(4, ELOCalculator.calculateLevel(1600), "1600应该是4级");
    }

    @Test
    @DisplayName("等级范围应该正确")
    void levelRangeShouldBeCorrect() {
        int[] range1 = ELOCalculator.getLevelRange(1);
        assertArrayEquals(new int[]{0, 1199}, range1, "1级范围应该是0-1199");

        int[] range2 = ELOCalculator.getLevelRange(2);
        assertArrayEquals(new int[]{1200, 1399}, range2, "2级范围应该是1200-1399");

        int[] range3 = ELOCalculator.getLevelRange(3);
        assertArrayEquals(new int[]{1400, 1599}, range3, "3级范围应该是1400-1599");
    }

    @Test
    @DisplayName("预估积分变化应该正确")
    void estimatedRatingChangeShouldBeCorrect() {
        int change = ELOCalculator.estimateRatingChange(1500, 1500, true);
        assertEquals(16, change, "同分获胜预估变化应该为16");

        int change2 = ELOCalculator.estimateRatingChange(1500, 1500, false);
        assertEquals(-16, change2, "同分失败预估变化应该为-16");
    }

    @Test
    @DisplayName("积分变化应该对称")
    void ratingChangeShouldBeSymmetric() {
        int[] result1 = ELOCalculator.calculateRatingChange(1500, 1400);
        int[] result2 = ELOCalculator.calculateRatingChange(1400, 1500);

        assertEquals(result1[2], -result2[3], "获胜方增加应该等于失败方减少");
        assertEquals(result1[3], -result2[2], "对称对局积分变化应该一致");
    }
}
