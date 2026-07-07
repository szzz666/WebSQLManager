package top.szzz666.database;

import top.szzz666.model.ColumnDefinition;
import top.szzz666.model.TableInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 方言实现
 */
public class MysqlDialect extends AbstractDialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String driverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "`" + name.replace("`", "``") + "`";
    }

    @Override
    public String buildPagedSql(String sql, long offset, int limit) {
        // MySQL 使用 LIMIT offset, limit
        if (offset <= 0) {
            return sql + " LIMIT " + limit;
        }
        return sql + " LIMIT " + offset + ", " + limit;
    }

    @Override
    public String buildCreateTableSql(String tableName, List<ColumnDefinition> columns, String tableComment) {
        String sql = super.buildCreateTableSql(tableName, columns, tableComment);
        if (tableComment != null && !tableComment.isEmpty()) {
            sql += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='" + tableComment.replace("'", "''") + "'";
        } else {
            sql += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        return sql;
    }

    @Override
    public String buildColumnDefSql(ColumnDefinition col) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(col.getName())).append(" ");
        sb.append(col.getType() == null ? "VARCHAR" : col.getType().toUpperCase());
        if (col.getLength() > 0) {
            sb.append("(").append(col.getLength());
            if (col.getScale() > 0) sb.append(",").append(col.getScale());
            sb.append(")");
        }
        if (col.isAutoIncrement()) sb.append(" AUTO_INCREMENT");
        if (!col.isNullable()) sb.append(" NOT NULL");
        if (col.isUnique()) sb.append(" UNIQUE");
        if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
            sb.append(" DEFAULT ").append(col.getDefaultValue());
        }
        if (col.getComment() != null && !col.getComment().isEmpty()) {
            sb.append(" COMMENT '").append(col.getComment().replace("'", "''")).append("'");
        }
        return sb.toString();
    }

    @Override
    public String buildModifyColumnSql(String tableName, ColumnDefinition col) {
        // MySQL 支持 MODIFY COLUMN
        return "ALTER TABLE " + quoteIdentifier(tableName) +
                " MODIFY COLUMN " + buildColumnDefSql(col);
    }

    @Override
    public String buildInsertSql(String tableName, List<String> columns, String mode) {
        String prefix = "INSERT INTO";
        if ("replace".equalsIgnoreCase(mode)) {
            prefix = "REPLACE INTO";
        } else if ("ignore".equalsIgnoreCase(mode)) {
            prefix = "INSERT IGNORE INTO";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(" ").append(quoteIdentifier(tableName)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quoteIdentifier(columns.get(i)));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String buildAddColumnSql(String tableName, ColumnDefinition col) {
        return "ALTER TABLE " + quoteIdentifier(tableName) +
                " ADD COLUMN " + buildColumnDefSql(col);
    }

    @Override
    public String buildRenameColumnSql(String tableName, String oldName, String newName, ColumnDefinition col) {
        // MySQL 8+ 支持 RENAME COLUMN，也可用 CHANGE
        return "ALTER TABLE " + quoteIdentifier(tableName) +
                " RENAME COLUMN " + quoteIdentifier(oldName) +
                " TO " + quoteIdentifier(newName);
    }

    @Override
    public boolean supportsMultiColumnAlter() {
        return true;
    }

    @Override
    public String buildTruncateTableSql(String tableName) {
        return "TRUNCATE TABLE " + quoteIdentifier(tableName);
    }

    @Override
    public List<TableInfo> listTables(Connection conn) throws SQLException {
        // 使用通用元数据，但额外查询行数和大小
        List<TableInfo> tables = super.listTables(conn);
        // 查询 information_schema 获取行数和大小
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT TABLE_NAME, TABLE_ROWS, DATA_LENGTH, CREATE_TIME, UPDATE_TIME, TABLE_COMMENT " +
                        "FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()");
             ResultSet rs = stmt.executeQuery()) {
            java.util.Map<String, TableInfo> map = new java.util.HashMap<>();
            for (TableInfo t : tables) map.put(t.getName(), t);
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                TableInfo t = map.get(name);
                if (t != null) {
                    t.setRowCount(rs.getLong("TABLE_ROWS"));
                    t.setDataSize(rs.getLong("DATA_LENGTH"));
                    t.setCreateTime(rs.getString("CREATE_TIME"));
                    t.setUpdateTime(rs.getString("UPDATE_TIME"));
                    String comment = rs.getString("TABLE_COMMENT");
                    if (comment != null && !comment.isEmpty()) t.setComment(comment);
                }
            }
        } catch (SQLException ignored) {
            // 某些 MySQL 版本可能没有 information_schema 访问权限，忽略
        }
        return tables;
    }

    @Override
    public long countRows(Connection conn, String tableName) throws SQLException {
        // MySQL 大表 COUNT(*) 慢，但这里保持精确
        return super.countRows(conn, tableName);
    }
}
