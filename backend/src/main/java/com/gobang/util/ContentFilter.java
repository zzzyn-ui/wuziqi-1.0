package com.gobang.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 内容过滤工具
 * 用于过滤聊天消息中的敏感词、特殊字符等
 */
public class ContentFilter {

    private static final Logger logger = LoggerFactory.getLogger(ContentFilter.class);

    /** 敏感词集合 */
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>(Arrays.asList(
        // 脏话类
        "傻逼", "傻B", "煞笔", "傻X", "妈的", "妈b", "妈逼",
        "操你", "草你", "艹你", "日你", "干你",
        "废物", "垃圾", "人渣", "败类", "低能", "智障",
        "死", "杀", "砍", "弄死", "废了",

        // 辱骂类
        "白痴", "白吃", "蠢货", "蠢材", "笨蛋", "傻瓜",
        "弱智", "脑残", "脑瘫", "智障",
        "滚蛋", "滚粗", "去死", "找死",

        // 禁止广告
        "加微信", "加qq", "加QQ", "代练", "外挂", "脚本",
        "买号", "卖号", "换号", "金币", "充值",

        // 政治敏感
        "法轮", "邪教", "恐怖", "炸弹", "爆炸",

        // 色情相关
        "色情", "黄片", "AV", "性服务", "约炮",

        // 赌博相关
        "博彩", "赌博", "六合彩", "时时彩", "百家乐"
    ));

    /** 敏感词替换字符 */
    private static final String REPLACEMENT = "***";

    /** URL正则表达式 */
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://|www\\.|\\.)?[a-zA-Z0-9\\-]+\\.[a-zA-Z]{2,}(/\\S*)?",
        Pattern.CASE_INSENSITIVE
    );

    /** 手机号正则表达式 */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "1[3-9]\\d{9}"
    );

    /** QQ号正则表达式 */
    private static final Pattern QQ_PATTERN = Pattern.compile(
        "qq|QQ|Qq|Q Q|q q"
    );

    /** 微信号正则表达式 */
    private static final Pattern WECHAT_PATTERN = Pattern.compile(
        "wx|WX|Wx|W X|w x|微信|vx"
    );

    /** 邮箱正则表达式 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    /**
     * 过滤聊天内容
     * @param content 原始内容
     * @return 过滤后的内容
     */
    public static String filter(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String result = content;

        // 1. 敏感词过滤
        result = filterSensitiveWords(result);

        // 2. URL过滤
        result = filterUrls(result);

        // 3. 手机号过滤
        result = filterPhones(result);

        // 4. QQ/微信联系方式过滤
        result = filterContactInfo(result);

        // 5. 邮箱过滤
        result = filterEmails(result);

        // 6. 长度限制（最多500字符）
        if (result.length() > 500) {
            result = result.substring(0, 500) + "...";
        }

        return result;
    }

    /**
     * 过滤敏感词
     */
    private static String filterSensitiveWords(String content) {
        String result = content;
        for (String word : SENSITIVE_WORDS) {
            // 使用正则表达式全局替换
            result = result.replaceAll("(?i)" + Pattern.quote(word), REPLACEMENT);
        }
        return result;
    }

    /**
     * 过滤URL链接
     */
    private static String filterUrls(String content) {
        return URL_PATTERN.matcher(content).replaceAll(REPLACEMENT);
    }

    /**
     * 过滤手机号
     */
    private static String filterPhones(String content) {
        return PHONE_PATTERN.matcher(content).replaceAll(REPLACEMENT);
    }

    /**
     * 过滤QQ/微信联系方式
     */
    private static String filterContactInfo(String content) {
        String result = content;
        result = QQ_PATTERN.matcher(result).replaceAll(REPLACEMENT);
        result = WECHAT_PATTERN.matcher(result).replaceAll(REPLACEMENT);
        return result;
    }

    /**
     * 过滤邮箱
     */
    private static String filterEmails(String content) {
        return EMAIL_PATTERN.matcher(content).replaceAll(REPLACEMENT);
    }

    /**
     * 检查内容是否包含敏感信息
     * @param content 待检查内容
     * @return true表示包含敏感信息
     */
    public static boolean containsSensitive(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        // 检查敏感词
        for (String word : SENSITIVE_WORDS) {
            if (content.toLowerCase().contains(word.toLowerCase())) {
                return true;
            }
        }

        // 检查URL
        if (URL_PATTERN.matcher(content).find()) {
            return true;
        }

        // 检查手机号
        if (PHONE_PATTERN.matcher(content).find()) {
            return true;
        }

        // 检查邮箱
        if (EMAIL_PATTERN.matcher(content).find()) {
            return true;
        }

        return false;
    }

    /**
     * 清理HTML标签（防止XSS）
     * @param content 原始内容
     * @return 清理后的内容
     */
    public static String sanitizeHtml(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // 移除HTML标签
        return content.replaceAll("<[^>]*>", "");
    }
}
