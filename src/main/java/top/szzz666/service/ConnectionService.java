package top.szzz666.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.database.ConnectionManager;
import top.szzz666.database.DatabaseDialect;
import top.szzz666.model.ConnectionConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接管理服务
 * 封装连接的增删改查、测试、状态查询等业务逻辑
 */
public class ConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionService.class);

    private final ConnectionManager connectionManager;

    public ConnectionService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 列出所有连接配置（脱敏，不修改原始对象）
     */
    public List<ConnectionConfig> listConnections() {
        List<ConnectionConfig> list = connectionManager.listConfigs();
        // 返回副本进行脱敏，避免修改内存中的原始密码
        java.util.List<ConnectionConfig> result = new java.util.ArrayList<>();
        for (ConnectionConfig c : list) {
            result.add(copyWithoutPassword(c));
        }
        return result;
    }

    /**
     * 获取单个连接配置（脱敏，不修改原始对象）
     */
    public ConnectionConfig getConnection(String id) {
        ConnectionConfig c = connectionManager.getConfig(id);
        if (c != null) {
            return copyWithoutPassword(c);
        }
        return null;
    }

    /**
     * 创建副本并清除密码（不影响原始对象）
     */
    private ConnectionConfig copyWithoutPassword(ConnectionConfig original) {
        ConnectionConfig copy = new ConnectionConfig();
        copy.setId(original.getId());
        copy.setName(original.getName());
        copy.setType(original.getType());
        copy.setJdbcUrl(original.getJdbcUrl());
        copy.setUsername(original.getUsername());
        copy.setPassword(null);
        copy.setAutoReconnect(original.isAutoReconnect());
        copy.setCreatedAt(original.getCreatedAt());
        copy.setUpdatedAt(original.getUpdatedAt());
        return copy;
    }

    /**
     * 添加连接配置
     */
    public ConnectionConfig addConnection(ConnectionConfig config) {
        return connectionManager.addConfig(config);
    }

    /**
     * 更新连接配置
     * 如果密码为空，则保留原密码
     */
    public ConnectionConfig updateConnection(String id, ConnectionConfig config) {
        ConnectionConfig existing = connectionManager.getConfig(id);
        if (existing == null) {
            throw new IllegalArgumentException("连接配置不存在: " + id);
        }
        // 密码为空则保留原密码
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            config.setPassword(existing.getPassword());
        }
        return connectionManager.updateConfig(id, config);
    }

    /**
     * 删除连接配置
     */
    public boolean removeConnection(String id) {
        return connectionManager.removeConfig(id);
    }

    /**
     * 测试连接
     */
    public Map<String, Object> testConnection(ConnectionConfig config) {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            connectionManager.testConnection(config);
            long elapsed = System.currentTimeMillis() - start;
            result.put("success", true);
            result.put("message", "连接成功");
            result.put("elapsed", elapsed);
            result.put("type", config.getType() == null || config.getType().isEmpty()
                    ? ConnectionConfig.detectType(config.getJdbcUrl()) : config.getType());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            result.put("success", false);
            result.put("message", e.getMessage());
            result.put("elapsed", elapsed);
            logger.warn("测试连接失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 测试已保存的连接
     */
    public Map<String, Object> testSavedConnection(String id) {
        ConnectionConfig config = connectionManager.getConfig(id);
        if (config == null) {
            throw new IllegalArgumentException("连接配置不存在: " + id);
        }
        return testConnection(config);
    }

    /**
     * 连接状态信息（不触发自动重连）
     */
    public Map<String, Object> getConnectionStatus(String id) {
        Map<String, Object> status = new HashMap<>();
        ConnectionConfig config = connectionManager.getConfig(id);
        if (config == null) {
            status.put("exists", false);
            return status;
        }
        status.put("exists", true);
        status.put("name", config.getName());
        status.put("type", config.getType());
        // 检查是否活跃（不创建新连接）
        boolean active = connectionManager.isActive(id);
        status.put("active", active);
        if (active) {
            try {
                Connection conn = connectionManager.getActiveConnection(id);
                if (conn != null) {
                    DatabaseDialect dialect = connectionManager.getDialect(id);
                    status.put("version", dialect.getDatabaseVersion(conn));
                }
            } catch (Exception e) {
                status.put("error", e.getMessage());
            }
        }
        return status;
    }

    /**
     * 断开连接
     */
    public void disconnect(String id) {
        connectionManager.closeConnection(id);
    }
}
