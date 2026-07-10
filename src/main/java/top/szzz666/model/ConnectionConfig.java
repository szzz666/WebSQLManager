package top.szzz666.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 数据库连接配置
 */
@Data
@NoArgsConstructor
public class ConnectionConfig {
    /** 连接唯一标识 */
    private String id;
    /** 连接名称 */
    private String name;
    /** 数据库类型：sqlite / mysql */
    private String type;
    /** JDBC URL */
    private String jdbcUrl;
    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
    /** 是否自动重连 */
    private boolean autoReconnect = true;
    /** 创建时间 */
    private long createdAt;
    /** 更新时间 */
    private long updatedAt;

    public ConnectionConfig(String name, String type, String jdbcUrl, String username, String password) {
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.name = name;
        this.type = type;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    /**
     * 自动检测数据库类型
     */
    public static String detectType(String jdbcUrl) {
        if (jdbcUrl == null) return "unknown";
        String lower = jdbcUrl.toLowerCase();
        if (lower.startsWith("jdbc:sqlite:") || lower.startsWith("sqlite:")) return "sqlite";
        if (lower.startsWith("jdbc:mysql:")) return "mysql";
        if (lower.startsWith("jdbc:mariadb:")) return "mariadb";
        if (lower.startsWith("jdbc:postgresql:")) return "postgresql";
        if (lower.startsWith("jdbc:sqlserver:")) return "mssql";
        if (lower.startsWith("jdbc:oracle:")) return "oracle";
        if (lower.startsWith("jdbc:h2:")) return "h2";
        return "unknown";
    }

    /**
     * 脱敏后的密码（用于前端展示）
     */
    public String getMaskedPassword() {
        if (password == null || password.isEmpty()) return "";
        if (password.length() <= 2) return "*".repeat(password.length());
        return password.charAt(0) + "*".repeat(Math.max(4, password.length() - 2)) + password.charAt(password.length() - 1);
    }
}
