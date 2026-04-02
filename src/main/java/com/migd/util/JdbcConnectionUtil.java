package com.migd.util;

import com.migd.dto.DbConnInfo;

import java.sql.*;

public final class JdbcConnectionUtil {

    private static final String DRIVER = "org.postgresql.Driver";

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("PostgreSQL JDBC 드라이버를 찾을 수 없습니다: " + e.getMessage());
        }
    }

    private JdbcConnectionUtil() {
    }

    public static String buildUrl(String host, int port, String db) {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, db);
    }

    /**
     * DB 커넥션 생성. 호출자가 반드시 close() 해야 합니다.
     */
    public static Connection open(DbConnInfo info) throws SQLException {
        String url = buildUrl(info.host(), info.port(), info.db());
        return DriverManager.getConnection(url, info.user(), info.password());
    }

    /**
     * 연결 테스트. 성공 시 PostgreSQL 버전 문자열 반환.
     */
    public static String testConnection(String host, int port, String db,
                                        String user, String password) throws SQLException {
        String url = buildUrl(host, port, db);
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version()")) {
            return rs.next() ? rs.getString(1) : "connected";
        }
    }
}
