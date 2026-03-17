package com.gobang.core.rating;

/**
 * ELO积分计算器
 * 用于计算对局后的积分变化
 */
public class ELOCalculator {

    // ELO系统参数
    private static final double K_FACTOR = 32.0; // K值，影响积分变化幅度

    /**
     * 计算对局后的新积分
     *
     * @param playerRating 玩家当前积分
     * @param opponentRating 对手当前积分
     * @param actualScore 实际得分 (1=胜, 0.5=平, 0=负)
     * @return 新积分
     */
    public static int calculateNewRating(int playerRating, int opponentRating, double actualScore) {
        double expectedScore = calculateExpectedScore(playerRating, opponentRating);
        double ratingChange = K_FACTOR * (actualScore - expectedScore);
        return (int) Math.round(playerRating + ratingChange);
    }

    /**
     * 计算预期得分
     * 根据双方积分差距，计算胜率概率
     *
     * @param playerRating 玩家积分
     * @param opponentRating 对手积分
     * @return 预期得分 (0-1之间)
     */
    public static double calculateExpectedScore(int playerRating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10, (opponentRating - playerRating) / 400.0));
    }

    /**
     * 计算双方对局后的积分变化
     *
     * @param winnerRating 获胜方积分
     * @param loserRating 失败方积分
     * @return 数组[获胜方新积分, 失败方新积分, 获胜方变化, 失败方变化]
     */
    public static int[] calculateRatingChange(int winnerRating, int loserRating) {
        int newWinnerRating = calculateNewRating(winnerRating, loserRating, 1.0);
        int newLoserRating = calculateNewRating(loserRating, winnerRating, 0.0);

        return new int[]{
                newWinnerRating,
                newLoserRating,
                newWinnerRating - winnerRating,
                newLoserRating - loserRating
        };
    }

    /**
     * 计算平局后的积分变化
     *
     * @param player1Rating 玩家1积分
     * @param player2Rating 玩家2积分
     * @return 数组[玩家1新积分, 玩家2新积分, 玩家1变化, 玩家2变化]
     */
    public static int[] calculateDrawRatingChange(int player1Rating, int player2Rating) {
        int newPlayer1Rating = calculateNewRating(player1Rating, player2Rating, 0.5);
        int newPlayer2Rating = calculateNewRating(player2Rating, player1Rating, 0.5);

        return new int[]{
                newPlayer1Rating,
                newPlayer2Rating,
                newPlayer1Rating - player1Rating,
                newPlayer2Rating - player2Rating
        };
    }

    /**
     * 预估对局后的积分变化（不实际应用）
     *
     * @param playerRating 玩家积分
     * @param opponentRating 对手积分
     * @param win 是否获胜
     * @return 积分变化量（正数表示增加，负数表示减少）
     */
    public static int estimateRatingChange(int playerRating, int opponentRating, boolean win) {
        double actualScore = win ? 1.0 : 0.0;
        double expectedScore = calculateExpectedScore(playerRating, opponentRating);
        return (int) Math.round(K_FACTOR * (actualScore - expectedScore));
    }

    /**
     * 获取玩家等级对应的积分段
     *
     * @param level 等级
     * @return 该等级的积分范围 [min, max]
     */
    public static int[] getLevelRange(int level) {
        if (level <= 0) {
            return new int[]{0, 1199};
        }
        int min = 1200 + (level - 1) * 200;
        int max = min + 199;
        return new int[]{min, max};
    }

    /**
     * 根据积分计算等级
     *
     * @param rating 积分
     * @return 等级
     */
    public static int calculateLevel(int rating) {
        if (rating < 1200) {
            return 1;
        }
        return (rating - 1200) / 200 + 2;
    }
}
