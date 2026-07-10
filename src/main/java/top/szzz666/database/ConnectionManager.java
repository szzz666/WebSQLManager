package top.szzz666.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.model.ConnectionConfig;
import top.szzz666.tools.JsonUtil;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接管理器
 * - 管理连接配置的持久化（JSON 文件）
 * - 管理活跃的 JDBC 连接（带懒加载）
 * - 提供测试连接、获取连接、关闭连接等功能
 */
public class ConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private static final String CONFIG_FILE = "connections.json";

    /** 配置缓存：id -> ConnectionConfig */
    private final Map<String, ConnectionConfig> configs = new ConcurrentHashMap<>();
    /** 活跃 JDBC 连接缓存：id -> Connection（懒加载，单连接复用） */
    private final Map<String, Connection> activeConnections = new ConcurrentHashMap<>();

    private final File configFile;

    public ConnectionManager() {
        this.configFile = new File(CONFIG_FILE);
        loadConfigs();
    }

    // ==================== 配置持久化 ====================

    private synchronized void loadConfigs() {
        if (!configFile.exists()) {
            logger.info("连接配置文件不存在，将创建新的: {}", configFile.getAbsolutePath());
            return;
        }
        try {
            String json = java.nio.file.Files.readString(configFile.toPath());
            if (json.trim().isEmpty()) return;
            Type type = new com.google.gson.reflect.TypeToken<List<ConnectionConfig>>() {}.getType();
            List<ConnectionConfig> list = JsonUtil.fromJson(json, type);
            if (list != null) {
                for (ConnectionConfig c : list) {
                    if (c.getId() == null || c.getId().isEmpty()) {
                        c.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
                    }
                    configs.put(c.getId(), c);
                }
            }
            logger.info("已加载 {} 个数据库连接配置", configs.size());
        } catch (Exception e) {
            logger.error("加载连接配置失败", e);
        }
    }

    public synchronized void saveConfigs() {
        try {
            List<ConnectionConfig> list = new ArrayList<>(configs.values());
            String json = JsonUtil.toCompactJson(list);
            java.nio.file.Files.writeString(configFile.toPath(), json);
            logger.debug("连接配置已保存");
        } catch (Exception e) {
            logger.error("保存连接配置失败", e);
        }
    }

    // ==================== 配置 CRUD ====================

    public List<ConnectionConfig> listConfigs() {
        return new ArrayList<>(configs.values());
    }

    public ConnectionConfig getConfig(String id) {
        return configs.get(id);
    }

    public ConnectionConfig addConfig(ConnectionConfig config) {
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        if (config.getType() == null || config.getType().isEmpty()) {
            config.setType(ConnectionConfig.detectType(config.getJdbcUrl()));
        }
        long now = System.currentTimeMillis();
        if (config.getCreatedAt() == 0) config.setCreatedAt(now);
        config.setUpdatedAt(now);
        configs.put(config.getId(), config);
        saveConfigs();
        return config;
    }

    public ConnectionConfig updateConfig(String id, ConnectionConfig config) {
        ConnectionConfig existing = configs.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("连接配置不存在: " + id);
        }
        config.setId(id);
        config.setCreatedAt(existing.getCreatedAt());
        config.setUpdatedAt(System.currentTimeMillis());
        if (config.getType() == null || config.getType().isEmpty()) {
            config.setType(ConnectionConfig.detectType(config.getJdbcUrl()));
        }
        configs.put(id, config);
        // 配置变更，关闭旧连接
        closeConnection(id);
        saveConfigs();
        return config;
    }

    public boolean removeConfig(String id) {
        ConnectionConfig removed = configs.remove(id);
        if (removed != null) {
            closeConnection(id);
            saveConfigs();
            return true;
        }
        return false;
    }

    // ==================== 连接管理 ====================

    /**
     * 测试连接（不缓存）
     */
    public void testConnection(ConnectionConfig config) throws SQLException {
        DatabaseDialect dialect = DialectFactory.get(config);
        loadDriver(dialect);
        String url = config.getJdbcUrl();
        String user = config.getUsername();
        String pass = config.getPassword();
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            if (conn.isClosed()) {
                throw new SQLException("连接已关闭");
            }
            // 执行简单验证
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(dialect instanceof SqliteDialect
                         ? "SELECT 1" : "SELECT 1")) {
                if (!rs.next()) throw new SQLException("测试查询无结果");
            }
        }
    }

    /**
     * 获取活跃连接（懒加载，复用）
     */
    public Connection getConnection(String id) throws SQLException {
        ConnectionConfig config = configs.get(id);
        if (config == null) {
            throw new IllegalArgumentException("连接配置不存在: " + id);
        }
        Connection conn = activeConnections.get(id);
        if (conn != null) {
            try {
                if (conn.isClosed() || !conn.isValid(3)) {
                    activeConnections.remove(id);
                    conn = null;
                }
            } catch (SQLException e) {
                activeConnections.remove(id);
                conn = null;
            }
        }
        if (conn == null) {
            conn = createConnection(config);
            activeConnections.put(id, conn);
        }
        return conn;
    }

    private Connection createConnection(ConnectionConfig config) throws SQLException {
        DatabaseDialect dialect = DialectFactory.get(config);
        loadDriver(dialect);
        String url = config.getJdbcUrl();
        String user = config.getUsername();
        String pass = config.getPassword();
        Connection conn;
        if (user != null && !user.isEmpty()) {
            conn = DriverManager.getConnection(url, user, pass);
        } else {
            conn = DriverManager.getConnection(url);
        }
        // SQLite 需要禁用自动提交以支持事务？保持自动提交便于操作
        conn.setAutoCommit(true);
        logger.info("已建立数据库连接: {} ({})", config.getName(), config.getType());
        return conn;
    }

    private void loadDriver(DatabaseDialect dialect) throws SQLException {
        try {
            Class.forName(dialect.driverClassName());
        } catch (ClassNotFoundException e) {
            throw new SQLException("数据库驱动未找到: " + dialect.driverClassName() +
                    "，请检查依赖。", e);
        }
    }

    /**
     * 检查连接是否活跃（不创建新连接）
     */
    public boolean isActive(String id) {
        Connection conn = activeConnections.get(id);
        if (conn == null) return false;
        try {
            return !conn.isClosed() && conn.isValid(3);
        } catch (SQLException e) {
            activeConnections.remove(id);
            return false;
        }
    }

    /**
     * 获取活跃连接（不创建新连接），返回 null 表示无活跃连接
     */
    public Connection getActiveConnection(String id) {
        Connection conn = activeConnections.get(id);
        if (conn == null) return null;
        try {
            if (conn.isClosed() || !conn.isValid(3)) {
                activeConnections.remove(id);
                return null;
            }
            return conn;
        } catch (SQLException e) {
            activeConnections.remove(id);
            return null;
        }
    }

    /**
     * 关闭指定连接
     */
    public void closeConnection(String id) {
        Connection conn = activeConnections.remove(id);
        if (conn != null) {
            try {
                if (!conn.isClosed()) conn.close();
                logger.info("已关闭数据库连接: {}", id);
            } catch (SQLException e) {
                logger.warn("关闭连接失败: {}", id, e);
            }
        }
    }

    /**
     * 关闭所有连接
     */
    public void closeAll() {
        for (String id : new ArrayList<>(activeConnections.keySet())) {
            closeConnection(id);
        }
    }

    /**
     * 获取指定连接的方言
     */
    public DatabaseDialect getDialect(String connectionId) {
        ConnectionConfig config = configs.get(connectionId);
        if (config == null) {
            throw new IllegalArgumentException("连接配置不存在: " + connectionId);
        }
        return DialectFactory.get(config);
    }

    /**
     * 获取活跃连接数量
     */
    public int getActiveCount() {
        return activeConnections.size();
    }
}
