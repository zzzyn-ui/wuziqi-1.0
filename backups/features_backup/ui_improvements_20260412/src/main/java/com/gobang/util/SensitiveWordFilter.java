package com.gobang.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 敏感词过滤器
 * 使用 DFA 算法实现高效过滤
 */
public class SensitiveWordFilter {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordFilter.class);

    private final Map<String, Object> sensitiveWordMap = new HashMap<>();
    private static final String DEFAULT_REPLACEMENT = "***";
    private static final String SENSITIVE_WORDS_FILE = "sensitive-words.txt";

    public SensitiveWordFilter() {
        initSensitiveWords();
    }

    /**
     * 初始化敏感词库
     */
    private void initSensitiveWords() {
        Set<String> words = loadDefaultWords();

        for (String word : words) {
            addWordToMap(word);
        }

        logger.info("Loaded {} sensitive words", words.size());
    }

    /**
     * 加载默认敏感词
     */
    private Set<String> loadDefaultWords() {
        Set<String> words = new HashSet<>();

        // 尝试从文件加载
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SENSITIVE_WORDS_FILE)) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        words.add(line);
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("No sensitive words file found, using defaults");
        }

        // 如果文件不存在，使用默认敏感词列表
        if (words.isEmpty()) {
            words.addAll(getDefaultSensitiveWords());
        }

        return words;
    }

    /**
     * 获取默认敏感词列表
     */
    private Set<String> getDefaultSensitiveWords() {
        return new HashSet<>(Arrays.asList(
                // 政治相关
                "法轮", "邪教", "暴动", "颠覆", "分裂",
                // 色情相关
                "色情", "淫秽", "裸聊", "性服务",
                // 暴力相关
                "杀人", "炸弹", "恐怖", "袭击",
                // 赌博相关
                "赌博", "博彩", "六合彩", "赌场",
                // 毒品相关
                "毒品", "大麻", "海洛因", "冰毒",
                // 诈骗相关
                "刷单", "兼职打字", "日赚五百", "代练",
                // 广告相关
                "代练", "代打", "外挂", "脚本",
                // 其他
                "GM", "管理员", "官方客服"
        ));
    }

    /**
     * 将敏感词添加到 DFA 树
     */
    private void addWordToMap(String word) {
        Map<String, Object> nowMap = sensitiveWordMap;
        for (int i = 0; i < word.length(); i++) {
            char key = word.charAt(i);
            Object wordMap = nowMap.get(String.valueOf(key));

            if (wordMap != null) {
                nowMap = (Map<String, Object>) wordMap;
            } else {
                Map<String, Object> newWordMap = new HashMap<>();
                nowMap.put(String.valueOf(key), newWordMap);
                nowMap = newWordMap;
            }

            if (i == word.length() - 1) {
                nowMap.put("isEnd", "1");
            }
        }
    }

    /**
     * 检查文本是否包含敏感词
     */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            int matchFlag = checkSensitiveWord(text, i);
            if (matchFlag > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤敏感词
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text);
        int begin = 0;

        for (int i = 0; i < text.length(); i++) {
            int matchFlag = checkSensitiveWord(text, i);
            if (matchFlag > 0) {
                String sensitiveWord = text.substring(i, i + matchFlag);
                result.replace(begin + i, begin + i + matchFlag, DEFAULT_REPLACEMENT);
                i += matchFlag - 1;
            }
        }

        return result.toString();
    }

    /**
     * 获取文本中的敏感词列表
     */
    public Set<String> getSensitiveWords(String text) {
        Set<String> words = new HashSet<>();

        if (text == null || text.isEmpty()) {
            return words;
        }

        for (int i = 0; i < text.length(); i++) {
            int length = checkSensitiveWord(text, i);
            if (length > 0) {
                words.add(text.substring(i, i + length));
                i += length - 1;
            }
        }

        return words;
    }

    /**
     * 检查敏感词（返回敏感词长度）
     */
    private int checkSensitiveWord(String text, int beginIndex) {
        if (sensitiveWordMap.isEmpty()) {
            return 0;
        }

        Map<String, Object> nowMap = sensitiveWordMap;
        int matchFlag = 0;
        int maxMatch = 0;

        for (int i = beginIndex; i < text.length(); i++) {
            char key = text.charAt(i);
            Object wordMap = nowMap.get(String.valueOf(key));

            if (wordMap != null) {
                nowMap = (Map<String, Object>) wordMap;
                matchFlag++;

                if (nowMap.containsKey("isEnd")) {
                    maxMatch = matchFlag;
                }
            } else {
                break;
            }
        }

        return maxMatch;
    }

    /**
     * 添加敏感词
     */
    public void addSensitiveWord(String word) {
        if (word != null && !word.isEmpty()) {
            addWordToMap(word);
        }
    }

    /**
     * 移除敏感词（需要重新初始化）
     */
    public void removeSensitiveWord(String word) {
        // 简单实现：重新初始化
        sensitiveWordMap.clear();
        initSensitiveWords();
    }
}
