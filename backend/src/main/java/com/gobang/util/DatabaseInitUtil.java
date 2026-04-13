package com.gobang.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

/**
 * 数据库初始化工具
 * 用于执行SQL脚本文件
 */
public class DatabaseInitUtil {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitUtil.class);

    /**
     * 执行SQL脚本文件
     * @param dataSource 数据源
     * @param sqlFilePath SQL文件路径
     * @return 是否执行成功
     */
    public static boolean executeSqlFile(DataSource dataSource, String sqlFilePath) {
        try {
            String content = Files.readString(Paths.get(sqlFilePath), StandardCharsets.UTF_8);
            List<String> statements = parseSqlStatements(content);

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (Statement stmt = conn.createStatement()) {
                    for (String sql : statements) {
                        if (sql.trim().isEmpty()) continue;
                        try {
                            stmt.execute(sql);
                        } catch (Exception e) {
                            logger.warn("SQL执行警告: {}", e.getMessage());
                            logger.debug("失败的SQL: {}", sql);
                        }
                    }
                    conn.commit();
                    logger.info("成功执行SQL文件: {}", sqlFilePath);
                    return true;
                }
            } catch (java.sql.SQLException e) {
                logger.error("执行SQL文件失败: {}", sqlFilePath, e);
                return false;
            }
        } catch (IOException e) {
            logger.error("读取SQL文件失败: {}", sqlFilePath, e);
            return false;
        }
    }

    /**
     * 解析SQL语句
     */
    private static List<String> parseSqlStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过注释
            if (trimmed.startsWith("--") || trimmed.startsWith("#")) {
                continue;
            }

            // 跳过空行
            if (trimmed.isEmpty()) {
                continue;
            }

            currentStatement.append(line).append("\n");

            // 检查是否是语句结束
            if (trimmed.endsWith(";")) {
                String sql = currentStatement.toString();
                // 移除末尾的分号
                if (sql.endsWith(";")) {
                    sql = sql.substring(0, sql.length() - 1);
                }
                statements.add(sql);
                currentStatement = new StringBuilder();
            }
        }

        // 处理最后一个没有分号的语句
        if (currentStatement.length() > 0) {
            statements.add(currentStatement.toString().trim());
        }

        return statements;
    }

    /**
     * 检查表是否存在
     */
    public static boolean tableExists(DataSource dataSource, String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            var meta = conn.getMetaData();
            var tables = meta.getTables(null, null, tableName, null);
            return tables.next();
        } catch (Exception e) {
            logger.error("检查表存在失败: {}", tableName, e);
            return false;
        }
    }

    /**
     * 检查并初始化残局数据
     */
    public static boolean initPuzzleData(DataSource dataSource) {
        // 检查表是否存在
        if (!tableExists(dataSource, "puzzles")) {
            logger.info("puzzles表不存在，创建表结构...");
            String schemaPath = "src/main/resources/sql/puzzle_schema.sql";
            if (!executeSqlFile(dataSource, schemaPath)) {
                logger.error("创建表结构失败");
                return false;
            }
        }

        // 检查是否有数据
        try {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM puzzles")) {

                if (rs.next() && rs.getInt("cnt") == 0) {
                    logger.info("残局数据为空，初始化数据...");
                    String dataPath = "src/main/resources/sql/puzzle_classic.sql";
                    return executeSqlFile(dataSource, dataPath);
                } else {
                    logger.info("残局数据已存在，跳过初始化");
                    return true;
                }
            }
        } catch (java.sql.SQLException e) {
            logger.error("检查残局数据失败", e);
            return false;
        }
    }
}
