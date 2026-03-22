package com.gobang.core.rating;

/**
 * 五子棋积分计算器
 * 使用Elo等级分系统
 */
public class RatingCalculator {

    // Elo系统的K值
    private static final int K_FACTOR = 32;

    /**
     * 计算新的积分
     * @param winnerRating 赢方当前积分
     * @param loserRating 输方当前积分
     * @param isDraw 是否平局
     * @return [赢方新积分, 输方新积分]
     */
    public static int[] calculateNewRatings(int winnerRating, int loserRating, boolean isDraw) {
        if (isDraw) {
            // 平局情况
            double expectedWinner = getExpectedScore(winnerRating, loserRating);
            double expectedLoser = getExpectedScore(loserRating, winnerRating);

            int newWinnerRating = (int) Math.round(winnerRating + K_FACTOR * (0.5 - expectedWinner));
            int newLoserRating = (int) Math.round(loserRating + K_FACTOR * (0.5 - expectedLoser));

            return new int[]{newWinnerRating, newLoserRating};
        } else {
            // 有胜负情况
            double expectedWinner = getExpectedScore(winnerRating, loserRating);
            double expectedLoser = getExpectedScore(loserRating, winnerRating);

            int newWinnerRating = (int) Math.round(winnerRating + K_FACTOR * (1.0 - expectedWinner));
            int newLoserRating = (int) Math.round(loserRating + K_FACTOR * (0.0 - expectedLoser));

            return new int[]{newWinnerRating, newLoserRating};
        }
    }

    /**
     * 计算预期得分
     * @param ratingA 玩家A的积分
     * @param ratingB 玩家B的积分
     * @return 玩家A的预期得分 (0-1之间)
     */
    private static double getExpectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
    }

    /**
     * 计算积分变化
     * @param myRating 我的积分
     * @param opponentRating 对手积分
     * @param isWin 是否获胜
     * @param isDraw 是否平局
     * @return 积分变化值
     */
    public static int calculateRatingChange(int myRating, int opponentRating, boolean isWin, boolean isDraw) {
        double expectedScore = getExpectedScore(myRating, opponentRating);
        double actualScore;

        if (isDraw) {
            actualScore = 0.5;
        } else if (isWin) {
            actualScore = 1.0;
        } else {
            actualScore = 0.0;
        }

        return (int) Math.round(K_FACTOR * (actualScore - expectedScore));
    }
}
