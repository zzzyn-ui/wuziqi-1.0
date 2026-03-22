package com.gobang.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple SQL executor
 * Usage: java com.gobang.util.ExecuteSQL <sql-file>
 */
public class ExecuteSQL {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java com.gobang.util.ExecuteSQL <sql-file>");
            System.exit(1);
        }

        String sqlFile = args[0];
        System.out.println("Executing SQL file: " + sqlFile);

        // Database configuration
        String url = "jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String user = "root";
        String password = "password";

        // Read SQL file
        String content = Files.readString(Paths.get(sqlFile), StandardCharsets.UTF_8);
        List<String> statements = parseSqlStatements(content);

        System.out.println("Found " + statements.size() + " SQL statements");

        // Load MySQL driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL driver loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to load MySQL driver: " + e.getMessage());
            System.exit(1);
        }

        // Execute statements
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                int executed = 0;
                for (String sql : statements) {
                    if (sql.trim().isEmpty()) continue;

                    try {
                        stmt.execute(sql);
                        executed++;
                        if (executed % 5 == 0) {
                            System.out.println("Executed " + executed + " statements...");
                        }
                    } catch (Exception e) {
                        System.err.println("Warning: " + e.getMessage());
                    }
                }
                conn.commit();
                System.out.println("Successfully executed " + executed + " statements");

                // Verify puzzle data
                var rs = stmt.executeQuery("SELECT difficulty, COUNT(*) as cnt FROM puzzles GROUP BY difficulty");
                System.out.println("\nPuzzle data verification:");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("difficulty") + ": " + rs.getInt("cnt") + " puzzles");
                }
            }
        }
    }

    private static List<String> parseSqlStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            // Skip comments
            if (trimmed.startsWith("--") || trimmed.startsWith("#")) {
                continue;
            }

            // Skip empty lines
            if (trimmed.isEmpty()) {
                continue;
            }

            currentStatement.append(line).append("\n");

            // Check for statement end
            if (trimmed.endsWith(";")) {
                String sql = currentStatement.toString();
                if (sql.endsWith(";")) {
                    sql = sql.substring(0, sql.length() - 1);
                }
                statements.add(sql);
                currentStatement = new StringBuilder();
            }
        }

        // Handle last statement without semicolon
        if (currentStatement.length() > 0) {
            statements.add(currentStatement.toString().trim());
        }

        return statements;
    }
}
