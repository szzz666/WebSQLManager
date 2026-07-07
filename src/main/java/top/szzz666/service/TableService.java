package top.szzz666.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.szzz666.database.AbstractDialect;
import top.szzz666.database.ConnectionManager;
import top.szzz666.database.DatabaseDialect;
import top.szzz666.model.ColumnDefinition;
import top.szzz666.model.IndexInfo;
import top.szzz666.model.TableInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 表结构管理服务
 * 负责表的创建、删除、重命名、结构修改等
 */
public class TableService {
    private static final Logger logger = LoggerFactory.getLogger(TableService.class);

    private final ConnectionManager connectionManager;

    public TableService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 列出所有表
     */
    public List<TableInfo> listTables(String connId) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        return dialect.listTables(conn);
    }

    /**
     * 获取表详细信息
     */
    public TableInfo getTableInfo(String connId, String tableName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        TableInfo table = dialect.getTableInfo(conn, tableName);
        table.setRowCount(dialect.countRows(conn, tableName));
        return table;
    }

    /**
     * 获取表索引
     */
    public List<IndexInfo> listIndexes(String connId, String tableName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        return dialect.listIndexes(conn, tableName);
    }

    /**
     * 创建表
     */
    public void createTable(String connId, String tableName, List<ColumnDefinition> columns, String comment) throws SQLException {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("至少需要定义一列");
        }
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildCreateTableSql(tableName, columns, comment);
        logger.info("创建表: {}", sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 删除表
     */
    public void dropTable(String connId, String tableName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildDropTableSql(tableName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 重命名表
     */
    public void renameTable(String connId, String oldName, String newName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildRenameTableSql(oldName, newName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 清空表数据
     */
    public void truncateTable(String connId, String tableName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildTruncateTableSql(tableName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 添加列
     */
    public void addColumn(String connId, String tableName, ColumnDefinition col) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildAddColumnSql(tableName, col);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 删除列
     */
    public void dropColumn(String connId, String tableName, String columnName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildDropColumnSql(tableName, columnName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 修改列定义
     */
    public void modifyColumn(String connId, String tableName, ColumnDefinition col) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildModifyColumnSql(tableName, col);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 重命名列
     */
    public void renameColumn(String connId, String tableName, String oldName, String newName, ColumnDefinition col) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        String sql = dialect.buildRenameColumnSql(tableName, oldName, newName, col);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 批量修改表结构
     * 对于不支持多列 ALTER 的方言（如 SQLite），逐条执行
     */
    public List<String> alterTable(String connId, String tableName, List<ColumnDefinition> columnChanges) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        List<String> executed = new ArrayList<>();
        for (ColumnDefinition col : columnChanges) {
            String action = col.getAction() == null ? "ADD" : col.getAction().toUpperCase();
            String sql;
            try {
                switch (action) {
                    case "ADD":
                        sql = dialect.buildAddColumnSql(tableName, col);
                        break;
                    case "ALTER":
                    case "MODIFY":
                        sql = dialect.buildModifyColumnSql(tableName, col);
                        break;
                    case "DROP":
                        sql = dialect.buildDropColumnSql(tableName, col.getName());
                        break;
                    case "RENAME":
                        sql = dialect.buildRenameColumnSql(tableName, col.getOldName(), col.getName(), col);
                        break;
                    default:
                        continue;
                }
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
                executed.add(sql);
            } catch (SQLException e) {
                logger.error("执行表结构变更失败: {}", e.getMessage());
                throw e;
            }
        }
        return executed;
    }

    /**
     * 获取表数据量
     */
    public long countRows(String connId, String tableName) throws SQLException {
        Connection conn = connectionManager.getConnection(connId);
        DatabaseDialect dialect = connectionManager.getDialect(connId);
        return dialect.countRows(conn, tableName);
    }
}
