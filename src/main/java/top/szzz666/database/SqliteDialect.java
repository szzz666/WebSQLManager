package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;
import top.szzz666.model.IndexInfo;
import top.szzz666.model.TableInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * SQLite 方言实现
 *
 * 注意：SQLite 对 ALTER TABLE 的支持有限：
 *  - 支持 ADD COLUMN
 *  - 支持 RENAME COLUMN（3.25+）
 *  - 支持 RENAME TABLE
 *  - 不直接支持 DROP COLUMN / MODIFY COLUMN（3.35+ 开始支持 DROP COLUMN）
 *  - 复杂修改需通过"重建表"实现
 */
public class SqliteDialect extends AbstractDialect {

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public String driverClassName() {
        return "org.sqlite.JDBC";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String buildPagedSql(String sql, long offset, int limit) {
        // SQLite 支持 LIMIT offset, limit 或 LIMIT limit OFFSET offset
        if (offset <= 0) {
            return sql + " LIMIT " + limit;
        }
        return sql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public String buildModifyColumnSql(String tableName, ColumnDefinition col) {
        // SQLite 不直接支持 MODIFY COLUMN，需重建表
        // 此处返回提示，实际由上层 TableService 处理重建逻辑
        throw new UnsupportedOperationException(
                "SQLite 不直接支持修改列定义，请使用重建表方式。表: " + tableName);
    }

    @Override
    public boolean supportsMultiColumnAlter() {
        return false;
    }

    @Override
    public String buildRenameColumnSql(String tableName, String oldName, String newName, ColumnDefinition col) {
        // SQLite 3.25+ 支持 RENAME COLUMN
        return "ALTER TABLE " + quoteIdentifier(tableName) +
                " RENAME COLUMN " + quoteIdentifier(oldName) +
                " TO " + quoteIdentifier(newName);
    }

    @Override
    public long countRows(Connection conn, String tableName) throws SQLException {
        // SQLite 使用 SELECT COUNT(*) 较慢，大表可考虑近似，这里保持精确
        return super.countRows(conn, tableName);
    }

    @Override
    public List<TableInfo> listTables(Connection conn) throws SQLException {
        // SQLite 通过 sqlite_master 获取，类型为 table
        List<TableInfo> tables = new java.util.ArrayList<>();
        String sql = "SELECT name, type, sql FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                TableInfo t = new TableInfo();
                t.setName(rs.getString("name"));
                t.setTableType("view".equalsIgnoreCase(rs.getString("type")) ? "VIEW" : "BASE TABLE");
                tables.add(t);
            }
        }
        // 获取列信息使用通用 JDBC 元数据
        return tables;
    }
}
